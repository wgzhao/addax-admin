package com.wgzhao.addax.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.*;
import com.wgzhao.addax.admin.service.*;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static com.wgzhao.addax.admin.common.Constants.ADDAX_EXECUTE_TIME_OUT_SECONDS;
import static java.lang.Math.max;

@Component
@Slf4j
public class TaskQueueManagerRedisImpl implements TaskQueueManager {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Lazy // Lazy load to break circular dependency
    @Autowired
    private TableService tableService;

    @Autowired
    private SystemConfigService configService;

    @Autowired
    private JobContentService jobContentService;

    @Autowired
    private TargetService targetService;

    @Autowired
    private DictService dictService;

    @Autowired
    private AddaxLogService addaxLogService;

    @Autowired
    private EtlJourService jourService;

    @Autowired
    private AlertService alertService;

    private int concurrentLimit;
    private int enqueueCapacity;
    private String instanceId;
    private volatile boolean running = false;

    private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> new Thread(r, "etl-redis-worker"));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> new Thread(r, "etl-redis-scheduler"));

    private static final String QUEUE_PENDING = "addax:queue:pending";
    private static final String SET_PENDING_TIDS = "addax:set:pending_tids";
    private static final String HASH_RUNNING_JOBS = "addax:hash:running_jobs";
    private static final String COUNT_GLOBAL_CONCURRENCY = "addax:count:global_concurrency";
    private static final String PREFIX_SOURCE_CONCURRENCY = "addax:count:source_concurrency:";

    @PostConstruct
    public void init() {
        configService.loadConfig();
        this.concurrentLimit = configService.getConcurrentLimit();
        this.enqueueCapacity = configService.getQueueSize();
        this.instanceId = resolveInstanceId();
        running = true;

        // Start worker threads to listen for tasks
        for (int i = 0; i < concurrentLimit; i++) {
            workerPool.submit(this::dispatchLoop);
        }
        // Periodically recover stale tasks
        scheduler.scheduleWithFixedDelay(this::recoverStaleTasks, 60, 60, TimeUnit.SECONDS);

        log.info("Redis-backed queue started. concurrentLimit={}, instanceId={}", concurrentLimit, instanceId);
    }

    private String resolveInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    @Override
    public void scanAndEnqueueEtlTasks() {
        Long pendingCount = redisTemplate.opsForSet().size(SET_PENDING_TIDS);
        if (pendingCount == null) pendingCount = 0L;

        if (pendingCount >= enqueueCapacity) {
            log.warn("Redis queue is full ({} / {}), skipping scan.", pendingCount, enqueueCapacity);
            return;
        }

        List<EtlTable> tasks = tableService.getRunnableTasks();
        if (tasks.isEmpty()) return;

        int room = (int) (enqueueCapacity - pendingCount);
        int enqueued = 0;
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));

        for (EtlTable task : tasks) {
            if (enqueued >= room) break;
            if (addTaskToQueue(task, bizDate)) {
                enqueued++;
            }
        }
        log.info("Enqueued {} tasks from DB scan.", enqueued);
    }

    @Override
    public boolean addTaskToQueue(EtlTable etlTable) {
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        return addTaskToQueue(etlTable, bizDate);
    }

    private boolean addTaskToQueue(EtlTable etlTable, LocalDate bizDate) {
        try {
            String taskId = etlTable.getId().toString() + ":" + bizDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            // Use a Set to check for existence to avoid duplicates in the queue
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(SET_PENDING_TIDS, taskId))) {
                return false;
            }

            EtlJobQueue job = new EtlJobQueue();
            job.setTid(etlTable.getId());
            job.setBizDate(bizDate);
            job.setPriority(100);
            job.setMaxAttempts(etlTable.getRetryCnt() == null ? 3 : etlTable.getRetryCnt());

            String jobJson = objectMapper.writeValueAsString(job);

            // Add to set and push to list atomically using Lua script
            String script = "if redis.call('sadd', KEYS[1], ARGV[1]) == 1 then redis.call('lpush', KEYS[2], ARGV[2]); return 1 else return 0 end";
            Long result = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                    Arrays.asList(SET_PENDING_TIDS, QUEUE_PENDING), taskId, jobJson);

            return result != null && result > 0;
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize job for Redis queue", e);
            return false;
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                // Block and pop a task from the queue
                String jobJson = redisTemplate.opsForList().rightPop(QUEUE_PENDING, 30, TimeUnit.SECONDS);
                if (jobJson == null) {
                    continue;
                }

                EtlJobQueue job = objectMapper.readValue(jobJson, EtlJobQueue.class);
                EtlTable table = tableService.getTable(job.getTid());
                if (table == null) {
                    log.warn("Task metadata not found for tid={}, skipping.", job.getTid());
                    continue;
                }

                // Concurrency Check
                if (!acquireConcurrency(table)) {
                    // Re-queue the task if concurrency limit is reached
                    redisTemplate.opsForList().leftPush(QUEUE_PENDING, jobJson);
                    // Wait a bit before next attempt to avoid busy-looping
                    Thread.sleep(1000);
                    continue;
                }

                // Execute the job
                executeClaimedJob(job, table);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Redis worker thread interrupted.");
                break;
            } catch (Exception e) {
                log.error("Error in Redis dispatch loop", e);
            }
        }
    }

    private boolean acquireConcurrency(EtlTable table) {
        // Global concurrency
        Long globalCount = redisTemplate.opsForValue().increment(COUNT_GLOBAL_CONCURRENCY);
        if (globalCount != null && globalCount > concurrentLimit) {
            redisTemplate.opsForValue().decrement(COUNT_GLOBAL_CONCURRENCY);
            return false;
        }

        // Source-specific concurrency
        VwEtlTableWithSource source = tableService.getTableView(table.getId());
        Integer maxSourceConcurrency = source.getMaxConcurrency();
        if (maxSourceConcurrency != null && maxSourceConcurrency > 0) {
            String sourceConcurrencyKey = PREFIX_SOURCE_CONCURRENCY + table.getSid();
            Long sourceCount = redisTemplate.opsForValue().increment(sourceConcurrencyKey);
            if (sourceCount != null && sourceCount > maxSourceConcurrency) {
                redisTemplate.opsForValue().decrement(sourceConcurrencyKey);
                redisTemplate.opsForValue().decrement(COUNT_GLOBAL_CONCURRENCY); // Rollback global
                return false;
            }
        }
        return true;
    }

    private void releaseConcurrency(EtlTable table) {
        redisTemplate.opsForValue().decrement(COUNT_GLOBAL_CONCURRENCY);

        VwEtlTableWithSource source = tableService.getTableView(table.getId());
        Integer maxSourceConcurrency = source.getMaxConcurrency();
        if (maxSourceConcurrency != null && maxSourceConcurrency > 0) {
            String sourceConcurrencyKey = PREFIX_SOURCE_CONCURRENCY + table.getSid();
            redisTemplate.opsForValue().decrement(sourceConcurrencyKey);
        }
    }

    private void executeClaimedJob(EtlJobQueue job, EtlTable table) {
        long start = System.currentTimeMillis();
        String taskId = table.getId().toString() + ":" + job.getBizDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        try {
            // Mark as running
            job.setClaimedBy(instanceId);
            job.setClaimedAt(java.time.Instant.now());
            redisTemplate.opsForHash().put(HASH_RUNNING_JOBS, taskId, objectMapper.writeValueAsString(job));
            redisTemplate.opsForSet().remove(SET_PENDING_TIDS, taskId);

            // Execute the actual task logic
            TaskResultDto result = executeEtlTaskLogic(table, job.getBizDate());

            if (!result.success()) {
                handleTaskFailure(job, taskId, "Addax exited with non-zero status");
            }
        } catch (Exception e) {
            log.error("Failed to execute task tid={}", job.getTid(), e);
            handleTaskFailure(job, taskId, e.getMessage());
        } finally {
            // Release resources
            redisTemplate.opsForHash().delete(HASH_RUNNING_JOBS, taskId);
            releaseConcurrency(table);
            log.debug("Job tid={} from queue finished in {}ms.", job.getTid(), System.currentTimeMillis() - start);
        }
    }

    @Override
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable etlTable) {
        if (!acquireConcurrency(etlTable)) {
            return TaskResultDto.failure("系统繁忙，超出并发限制", 0);
        }
        long startTime = System.currentTimeMillis();
        try {
            // Manually triggered tasks use the default business date from config
            LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
            return executeEtlTaskLogic(etlTable, bizDate);
        } finally {
            releaseConcurrency(etlTable);
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.debug("Manual job tid={} finished in {}s.", etlTable.getId(), duration);
        }
    }

    private void handleTaskFailure(EtlJobQueue job, String taskId, String errorMessage) {
        job.setAttempts(job.getAttempts() + 1);
        if (job.getAttempts() >= job.getMaxAttempts()) {
            log.warn("Task tid={} failed after max attempts.", job.getTid());
            // Optionally move to a failed queue/set
        } else {
            // Re-queue for retry
            try {
                String jobJson = objectMapper.writeValueAsString(job);
                redisTemplate.opsForList().leftPush(QUEUE_PENDING, jobJson);
                redisTemplate.opsForSet().add(SET_PENDING_TIDS, taskId);
            } catch (JsonProcessingException e) {
                log.error("Failed to re-queue failed task tid={}", job.getTid(), e);
            }
        }
    }

    private TaskResultDto executeEtlTaskLogic(EtlTable task, LocalDate bizDate) {
        // This logic is mostly copied from TaskQueueManagerV2Impl
        // It should be extracted into a common service to avoid duplication.
        // For now, we duplicate it for simplicity.
        long taskId = task.getId();
        long startTime = System.currentTimeMillis();
        try {
            tableService.setRunning(task);
            String jobContent = jobContentService.getJobContent(taskId);
            if (jobContent == null || jobContent.isEmpty()) {
                return TaskResultDto.failure("Job content is empty", 0);
            }

            String bizDateStr = bizDate.format(DateTimeFormatter.ofPattern(task.getPartFormat() != null ? task.getPartFormat() : "yyyyMMdd"));
            jobContent = jobContent.replace("${logdate}", bizDateStr)
                                 .replace("${dw_clt_date}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                                 .replace("${dw_trade_date}", configService.getBizDate());

            if (task.getPartName() != null && !task.getPartName().isEmpty()) {
                targetService.addPartition(taskId, task.getTargetDb(), task.getTargetTable(), task.getPartName(), bizDateStr);
            }

            File tempFile = Files.createTempFile("addax-job-", ".json").toFile();
            Files.writeString(tempFile.toPath(), jobContent);

            String logName = String.format("%s.%s_%d.log", task.getTargetDb(), task.getTargetTable(), taskId);
            String cmd = String.format("%s/bin/addax.sh -p'-DjobName=%d -Dlog.file.name=%s' %s",
                    dictService.getAddaxHome(), taskId, logName, tempFile.getAbsolutePath());

            EtlJour etlJour = jourService.addJour(taskId, JourKind.COLLECT, cmd);
            TaskResultDto taskResult = CommandExecutor.executeWithResult(cmd, ADDAX_EXECUTE_TIME_OUT_SECONDS);

            String logContent = "";
            try {
                logContent = Files.readString(Path.of(dictService.getAddaxHome() + "/log/" + logName));
            } catch (IOException e) {
                log.error("Failed to read addax log file", e);
            }
            addaxLogService.insertLog(taskId, logContent);

            etlJour.setDuration(taskResult.durationSeconds());
            etlJour.setStatus(taskResult.success());
            if (!taskResult.success()) {
                etlJour.setErrorMsg(taskResult.message());
            }
            jourService.saveJour(etlJour);

            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0);
            task.setDuration(duration);
            if (taskResult.success()) {
                tableService.setFinished(task);
            } else {
                tableService.setFailed(task);
                alertService.sendToWeComRobot("Task failed: " + taskId);
            }
            return new TaskResultDto(taskResult.success(), taskResult.message(), duration);
        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("ETL task execution failed: tid={}", taskId, e);
            task.setDuration(duration);
            tableService.setFailed(task);
            alertService.sendToWeComRobot(String.format("Task failed: %s, Error: %s", taskId, e.getMessage()));
            return TaskResultDto.failure("Execution exception: " + e.getMessage(), duration);
        }
    }

    private void recoverStaleTasks() {
        // Recover tasks that were running but the instance died
        Map<Object, Object> runningJobs = redisTemplate.opsForHash().entries(HASH_RUNNING_JOBS);
        for (Map.Entry<Object, Object> entry : runningJobs.entrySet()) {
            try {
                EtlJobQueue job = objectMapper.readValue((String) entry.getValue(), EtlJobQueue.class);
                // A more robust check would involve a lease time
                // For now, we assume if it's old, it's stale. This is NOT production-ready.
                if (job.getClaimedAt().isBefore(java.time.Instant.now().minus(Duration.ofHours(2)))) {
                    log.warn("Recovering stale task tid={}", job.getTid());
                    redisTemplate.opsForHash().delete(HASH_RUNNING_JOBS, entry.getKey());
                    handleTaskFailure(job, (String) entry.getKey(), "Recovered from stale state");
                }
            } catch (Exception e) {
                log.error("Failed to recover stale task", e);
            }
        }
    }

    @Override
    public void stopQueueMonitor() {
        this.running = false;
    }

    @Override
    public void startQueueMonitor() {
        if (!running) {
            running = true;
            for (int i = 0; i < concurrentLimit; i++) {
                workerPool.submit(this::dispatchLoop);
            }
        }
    }

    @Override
    public void restartQueueMonitor() {
        stopQueueMonitor();
        try {
            workerPool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        startQueueMonitor();
    }

    @Override
    public int clearQueue() {
        Long pendingSize = redisTemplate.opsForList().size(QUEUE_PENDING);
        redisTemplate.delete(Arrays.asList(QUEUE_PENDING, SET_PENDING_TIDS));
        return pendingSize != null ? pendingSize.intValue() : 0;
    }

    @Override
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("pendingInQueue", redisTemplate.opsForList().size(QUEUE_PENDING));
        status.put("runningTasks", redisTemplate.opsForValue().get(COUNT_GLOBAL_CONCURRENCY));
        status.put("runningJobsDetail", redisTemplate.opsForHash().size(HASH_RUNNING_JOBS));
        return status;
    }

    @Override
    public void truncateQueueExceptRunningTasks() {
        // In Redis implementation, we just clear the pending queue. Running tasks are in a separate hash.
        clearQueue();
    }

    @Override
    public boolean addTaskToQueue(long tableId) {
        EtlTable table = tableService.getTable(tableId);
        return table != null && addTaskToQueue(table);
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        workerPool.shutdownNow();
        log.info("Redis Task Queue Manager has been shut down.");
    }
}

