package com.wgzhao.addax.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.*;
import com.wgzhao.addax.admin.service.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class TaskQueueManagerRedisImpl extends AbstractTaskQueueManager implements ApplicationListener<ContextClosedEvent> {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Lazy // Lazy load to break circular dependency
    @Autowired
    private TableService tableService;


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
            job.setAttempts(0); // Initialize attempts
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
                String jobJson = redisTemplate.opsForList().rightPop(QUEUE_PENDING, 5, TimeUnit.SECONDS);
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
                log.info("Redis worker thread interrupted because application is shutting down.");
                break;
            } catch (Exception e) {
                if (running) {
                    log.error("Error in Redis dispatch loop", e);
                }
            }
        }
        log.info("Dispatch loop finished.");
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
        // acquire concurrency lock
        if (!acquireConcurrency(table)) {
            log.warn("系统繁忙，超出并发限制, 无法执行任务 tid={}", job.getTid());
            // re-enqueue
            try {
                String jobJson = objectMapper.writeValueAsString(job);
                redisTemplate.opsForList().leftPush(QUEUE_PENDING, jobJson);
            } catch (JsonProcessingException e) {
                log.error("Failed to re-queue job", e);
            }
            return;
        }

        // Mark as running and set start_time
        tableService.setRunning(table);
        job.setClaimedBy(instanceId);
        job.setClaimedAt(java.time.Instant.now());
        try {
            redisTemplate.opsForHash().put(HASH_RUNNING_JOBS, taskId, objectMapper.writeValueAsString(job));
            redisTemplate.opsForSet().remove(SET_PENDING_TIDS, taskId);
            TaskResultDto taskResultDto = executeEtlTaskWithConcurrencyControl(table, job.getBizDate());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize running job for Redis", e);
            tableService.setFailed(table);
        } finally {
            // Release resources
            redisTemplate.opsForHash().delete(HASH_RUNNING_JOBS, taskId);
            releaseConcurrency(table);
            log.debug("Job tid={} from queue finished in {}ms.", job.getTid(), System.currentTimeMillis() - start);
        }

    }

    private void handleTaskFailure(EtlJobQueue job, String taskId, String errorMessage) {
        int currentAttempts = (job.getAttempts() == null ? 0 : job.getAttempts()) + 1;
        job.setAttempts(currentAttempts);

        if (currentAttempts >= job.getMaxAttempts()) {
            log.warn("Task tid={} failed after max attempts. Error: {}", job.getTid(), errorMessage);
            // Optionally move to a failed queue/set
        } else {
            // Re-queue for retry
            try {
                log.info("Re-queuing task tid={} for attempt {}/{}", job.getTid(), currentAttempts + 1, job.getMaxAttempts());
                String jobJson = objectMapper.writeValueAsString(job);
                redisTemplate.opsForList().leftPush(QUEUE_PENDING, jobJson);
                redisTemplate.opsForSet().add(SET_PENDING_TIDS, taskId);
            } catch (JsonProcessingException e) {
                log.error("Failed to re-queue failed task tid={}", job.getTid(), e);
            }
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
        workerPool.shutdownNow();
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
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
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

    public void shutdown() {
        log.info("Shutting down Redis Task Queue Manager...");
        running = false;
        scheduler.shutdownNow();
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Worker pool did not terminate in 30 seconds. Forcing shutdown.");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for worker pool to terminate", e);
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Redis Task Queue Manager has been shut down.");
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdown();
    }
}

