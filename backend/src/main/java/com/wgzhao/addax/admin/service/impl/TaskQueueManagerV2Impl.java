package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.*;
import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.service.*;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.wgzhao.addax.admin.common.Constants.ADDAX_EXECUTE_TIME_OUT_SECONDS;
import static com.wgzhao.addax.admin.common.Constants.SCHEMA_REFRESH_LOCK_KEY;
import static java.lang.Math.max;

/**
 * 采集任务队列管理器 - 使用 PostgreSQL 持久化队列 + LISTEN/NOTIFY
 * 并发与锁控制从数据库迁移为 Redis
 */
@Component
@Primary
@Slf4j
public class TaskQueueManagerV2Impl implements TaskQueueManager {
    @Autowired
    private DictService dictService;
    @Autowired
    private AddaxLogService addaxLogService;
    @Autowired
    private AlertService alertService;
    @Autowired
    private TableService tableService;
    @Autowired
    private EtlJourService jourService;
    @Autowired
    private SystemConfigService configService;
    @Autowired
    private JobContentService jobContentService;
    @Autowired
    private TargetService targetService;
    @Autowired
    private SystemFlagService systemFlagService;
    @Autowired
    private EtlJobQueueService jobQueueService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RedisLockService redisLockService;

    // 并发限制 & 入队容量
    private int concurrentLimit;
    private int enqueueCapacity;

    // 本地并发统计（仅用于快速短期判断/指标）
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, AtomicInteger> sourceRunningTaskCount = new ConcurrentHashMap<>();

    // token maps: store redis tokens associated with a jobId
    private final ConcurrentHashMap<Long, String> jobLockToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> jobGlobalPermitToken = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> jobSourcePermitToken = new ConcurrentHashMap<>();

    private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "etl-worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "etl-scheduler");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = false;
    private String instanceId;

    // 在本地任务完成后触发调度的合并标记，避免并发任务完成时重复提交大量 dispatch 任务
    private final AtomicBoolean dispatchScheduled = new AtomicBoolean(false);

    // backoff 策略
    private static final int BACKOFF_MIN_SECONDS = 30;
    private static final int BACKOFF_MAX_SECONDS = 1800; // 30m
    private static final int BACKOFF_FACTOR = 2;

    // 轮询间隔 & 租约
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 3;
    private static final int DEFAULT_LEASE_SECONDS = 7300; // 2小时 + 5分钟 buffer

    @PostConstruct
    public void init() {
        configService.loadConfig();
        this.concurrentLimit = configService.getConcurrentLimit();
        this.enqueueCapacity = configService.getQueueSize();
        this.instanceId = resolveInstanceId();

        running = true;

        // 启动 LISTEN 监听器
        scheduler.execute(this::listenLoop);
        // 启动定时轮询兜底
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch, 1, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        // 启动租约回收（仍保留 DB 层回收以保证兼容）
        scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
        log.info("Redis-backed arbitration queue started. concurrentLimit={}, enqueueCapacity={}, instanceId={}", concurrentLimit, enqueueCapacity, instanceId);
    }

    private String resolveInstanceId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName();
            return host + "-" + pid;
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 扫描并入队（DB 持久化），遵守容量限制与去重
     */
    public void scanAndEnqueueEtlTasks() {
        try {
            long pending = jobQueueService.countPending();
            if (pending >= enqueueCapacity) {
                log.warn("队列已达容量上限: {}/{}，暂停入队", pending, enqueueCapacity);
                return;
            }
            List<EtlTable> tasks = tableService.getRunnableTasks();
            if (tasks.isEmpty()) {
                return;
            }
            int room = (int) Math.max(0, enqueueCapacity - pending);
            int enqueued = 0, skipped = 0;
            LocalDate bizDate = LocalDate.parse(configService.getBizDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
            for (EtlTable t : tasks) {
                if (enqueued >= room) {
                    skipped += 1;
                    continue;
                }
                try {
                    int added = jobQueueService.enqueue(t, bizDate, 100);
                    if (added > 0) {
                        enqueued++;
                    } else {
                        skipped++;
                    }
                } catch (Exception ex) {
                    // 可能因为唯一约束冲突等导致无法入队
                    skipped++;
                    log.debug("入队跳过 tid={}, 原因={} ", t.getId(), ex.getMessage());
                }
            }
            log.info("入队完成: 成功 {} 个，跳过 {} 个，队列待处理 {}", enqueued, skipped, jobQueueService.countPending());
        } catch (Exception e) {
            log.error("扫描并入队失败", e);
            alertService.sendToWeComRobot("扫描采集任务失败: " + e.getMessage());
        }
    }

    private void listenLoop() {
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(true);
            stmt.execute("LISTEN etl_jobs");
            log.info("LISTEN on channel etl_jobs started");
            org.postgresql.PGConnection pgConn = conn.unwrap(org.postgresql.PGConnection.class);
            while (running) {
                try {
                    // 通过发送一条轻量查询来触发通知检查
                    stmt.execute("SELECT 1");
                    org.postgresql.PGNotification[] notes = pgConn.getNotifications();
                    if (notes != null && notes.length > 0) {
                        log.debug("收到 {} 条通知", notes.length);
                        // 每条通知尝试触发一次拉取与分发
                        for (org.postgresql.PGNotification ignored : notes) {
                            dispatchUntilFull();
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (SQLException se) {
                    log.warn("LISTEN 循环 SQL 异常，将重试", se);
                    TimeUnit.SECONDS.sleep(2);
                }
            }
        } catch (Exception e) {
            log.error("LISTEN 监听器异常退出", e);
        }
        log.info("LISTEN 监听器结束");
    }

    private void pollAndDispatch() {
        if (!running) return;
        dispatchUntilFull();
    }

    private void dispatchUntilFull() {
        // 使用 Redis 全局并发许可和数据源级许可来决定是否真正执行任务
        while (running && runningTaskCount.get() < concurrentLimit) {
            Optional<EtlJobQueue> maybe = jobQueueService.claimNext(instanceId, DEFAULT_LEASE_SECONDS);
            if (maybe.isEmpty()) {
                return;
            }
            EtlJobQueue job = maybe.get();
            EtlTable table = tableService.getTable(job.getTid());
            if (table == null) {
                log.warn("未找到 tid={} 对应的任务信息，跳过该任务", job.getTid());
                jobQueueService.completeFailure(job.getId(), "任务信息丢失");
                continue;
            }
            VwEtlTableWithSource source = tableService.getTableView(table.getId());
            Integer maxConcurrency = source.getMaxConcurrency();

            final long jobId = job.getId();
            final String jobLockKey = "etl:job:" + jobId + ":lock";
            final Duration jobLockTtl = Duration.ofSeconds(DEFAULT_LEASE_SECONDS);

            // Try to acquire per-job redis lock to ensure only one node proceeds with this job
            String jtoken = null;
            try {
                jtoken = redisLockService.tryLock(jobLockKey, jobLockTtl);
                if (jtoken == null) {
                    log.info("Could not acquire redis job lock {} for jobId={}, releasing DB claim", jobLockKey, jobId);
                    jobQueueService.releaseClaim(job.getId(), 5);
                    continue;
                }
                jobLockToken.put(jobId, jtoken);

                // Try to acquire global permit
                String globalPermitKey = "concurrent:holders";
                String globalToken = redisLockService.tryAcquirePermit(globalPermitKey, concurrentLimit, jobLockTtl);
                if (globalToken == null) {
                    log.info("Global concurrency limit reached, releasing job {}", jobId);
                    // cleanup
                    redisLockService.release(jobLockKey, jtoken);
                    jobLockToken.remove(jobId);
                    jobQueueService.releaseClaim(job.getId(), 5);
                    continue;
                }
                jobGlobalPermitToken.put(jobId, globalToken);

                // Try to acquire source-level permit if needed
                String sourcePermitToken = null;
                if (maxConcurrency != null && maxConcurrency > 0) {
                    String sourcePermitKey = "source:holders:" + table.getSid();
                    sourcePermitToken = redisLockService.tryAcquirePermit(sourcePermitKey, maxConcurrency, jobLockTtl);
                    if (sourcePermitToken == null) {
                        log.info("Source {} concurrency reached, releasing job {}", source.getCode(), jobId);
                        // cleanup global and job lock
                        redisLockService.release(globalPermitKey, globalToken);
                        jobGlobalPermitToken.remove(jobId);
                        redisLockService.release(jobLockKey, jtoken);
                        jobLockToken.remove(jobId);
                        jobQueueService.releaseClaim(job.getId(), 5);
                        // mark waiting
                        try {
                            tableService.setWaiting(table);
                        } catch (Exception e) {
                            log.warn("设置任务为 WAITING_COLLECT 失败 tid={}, err={}", table.getId(), e.getMessage());
                        }
                        continue;
                    }
                    jobSourcePermitToken.put(jobId, sourcePermitToken);
                }

                // Successfully acquired all required permits; now update local counters and submit
                int current = runningTaskCount.incrementAndGet();
                if (maxConcurrency != null && maxConcurrency > 0) {
                    sourceRunningTaskCount.computeIfAbsent(table.getSid(), k -> new AtomicInteger(0)).incrementAndGet();
                }
                log.info("领取任务 jobId={}, tid={}, attempts={}/{}, 当前本地并发 {}/{}, 数据源 {} 并发 {}/{}",
                        job.getId(), job.getTid(), job.getAttempts(), job.getMaxAttempts(),
                        current, concurrentLimit, source.getCode(),
                        sourceRunningTaskCount.getOrDefault(table.getSid(), new AtomicInteger(0)).get(),
                        maxConcurrency == null || maxConcurrency <= 0 ? "无限制" : maxConcurrency);

                workerPool.submit(() -> executeClaimedJob(job));

            } catch (Exception e) {
                log.error("Dispatch error for jobId={}", job.getId(), e);
                // best-effort cleanup
                if (jtoken != null) {
                    redisLockService.release(jobLockKey, jtoken);
                    jobLockToken.remove(jobId);
                }
                if (jobQueueService != null) {
                    try {
                        jobQueueService.releaseClaim(job.getId(), 5);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * 在本地任务完成释放并发槽后，异步触发一次调度。
     * 使用 dispatchScheduled 进行合并控制，避免并发完成时重复提交大量调度任务。
     */
    private void triggerDispatchAsync() {
        if (!running) {
            return;
        }
        // 如果已经有一次待执行的调度在排队/执行中，则不再重复提交
        if (!dispatchScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.execute(() -> {
                try {
                    // 清除标记，允许后续再次提交调度任务
                    dispatchScheduled.set(false);
                    // 尽力填满本节点的并发槽位
                    dispatchUntilFull();
                } catch (Throwable t) {
                    log.warn("本地触发调度任务执行异常", t);
                }
            });
        } catch (Exception e) {
            // 提交失败时清理标记，避免永远不再触发
            dispatchScheduled.set(false);
            log.warn("提交本地调度任务失败", e);
        }
    }

    private void executeClaimedJob(EtlJobQueue job) {
        long start = System.currentTimeMillis();
        // Schedule periodic redis-based lease/permit renewal while this job runs
        ScheduledFuture<?> renewer = null;
        final long jobId = job.getId();
        final String jobLockKey = "etl:job:" + jobId + ":lock";
        final Duration jobLockTtl = Duration.ofSeconds(DEFAULT_LEASE_SECONDS);

        try {
            int renewInterval = Math.max(30, DEFAULT_LEASE_SECONDS / 3);
            renewer = scheduler.scheduleAtFixedRate(() -> {
                try {
                    // renew job lock
                    String jt = jobLockToken.get(jobId);
                    if (jt != null) {
                        boolean ok = redisLockService.extend(jobLockKey, jt, jobLockTtl);
                        if (!ok) {
                            log.warn("Failed to renew redis job lock for jobId={}", jobId);
                        }
                    }
                    // renew global permit
                    String gk = jobGlobalPermitToken.get(jobId);
                    if (gk != null) {
                        boolean ok = redisLockService.extendPermit("concurrent:holders", gk, jobLockTtl);
                        if (!ok) {
                            log.warn("Failed to renew global permit for jobId={}", jobId);
                        }
                    }
                    // renew source permit
                    String sk = jobSourcePermitToken.get(jobId);
                    if (sk != null) {
                        String sourcePermitKey = "source:holders:" + (tableService.getTable(job.getTid()).getSid());
                        boolean ok = redisLockService.extendPermit(sourcePermitKey, sk, jobLockTtl);
                        if (!ok) {
                            log.warn("Failed to renew source permit for jobId={}", jobId);
                        }
                    }
                    // Still keep DB lease renewal as a fallback for compatibility
                    try {
                        boolean dbRenewed = jobQueueService.renewLease(job.getId(), instanceId, DEFAULT_LEASE_SECONDS);
                        if (!dbRenewed) {
                            log.warn("DB lease renewal failed jobId={}, instanceId={}", job.getId(), instanceId);
                        }
                    } catch (Exception e) {
                        log.warn("DB lease renewal exception jobId={}", job.getId(), e);
                    }
                } catch (Exception e) {
                    log.warn("Lease/permit renewal exception for jobId={}", jobId, e);
                }
            }, renewInterval, renewInterval, TimeUnit.SECONDS);

            EtlTable task = tableService.getTable(job.getTid());
            if (task == null) {
                throw new IllegalStateException("任务不存在 tid=" + job.getTid());
            }
            TaskResultDto taskResultDto = executeEtlTaskWithConcurrencyControl(task, job.getBizDate());
            if (taskResultDto.success()) {
                jobQueueService.completeSuccess(job.getId());
            } else {
                Duration backoff = computeBackoff(job.getAttempts());
                jobQueueService.failOrReschedule(job, "Addax 退出非0", backoff);
            }
        } catch (Exception e) {
            log.error("执行任务失败 jobId={} tid={}", job.getId(), job.getTid(), e);
            Duration backoff = computeBackoff(job.getAttempts());
            try {
                jobQueueService.failOrReschedule(job, e.getMessage(), backoff);
            } catch (Exception ignored) {
            }
        } finally {
            if (renewer != null) {
                try {
                    renewer.cancel(false);
                } catch (Exception ignored) {
                }
            }
            // 释放本地并发计数
            int afterGlobal = runningTaskCount.decrementAndGet();

            // 释放源级并发计数（本地指标）
            EtlTable table = tableService.getTable(job.getTid());
            if (table != null) {
                VwEtlTableWithSource source = tableService.getTableView(table.getId());
                if (source != null) {
                    Integer maxConcurrency = source.getMaxConcurrency();
                    if (maxConcurrency != null && maxConcurrency > 0) {
                        sourceRunningTaskCount.computeIfPresent(table.getSid(), (k, v) -> {
                            int after = v.decrementAndGet();
                            log.debug("数据源 {} 并发减少为 {}，maxConcurrency={}", k, after, maxConcurrency);
                            return v;
                        });
                    }
                }
            }

            log.debug("jobId={} 完成，耗时 {}s，当前本地并发 {}，当前队列待处理 {}",
                    job.getId(), (System.currentTimeMillis() - start) / 1000, afterGlobal, jobQueueService.countPending());

            // Release Redis tokens/permits and cleanup
            try {
                String sk = jobSourcePermitToken.remove(jobId);
                if (sk != null) {
                    String sourcePermitKey = "source:holders:" + (table != null ? table.getSid() : "unknown");
                    redisLockService.releasePermit(sourcePermitKey, sk);
                }
            } catch (Exception e) {
                log.warn("Failed to release source permit for jobId={}", jobId, e);
            }
            try {
                String gk = jobGlobalPermitToken.remove(jobId);
                if (gk != null) {
                    redisLockService.releasePermit("concurrent:holders", gk);
                }
            } catch (Exception e) {
                log.warn("Failed to release global permit for jobId={}", jobId, e);
            }
            try {
                String jt = jobLockToken.remove(jobId);
                if (jt != null) {
                    redisLockService.release(jobLockKey, jt);
                }
            } catch (Exception e) {
                log.warn("Failed to release job lock for jobId={}", jobId, e);
            }

            // 完成 DB 层任务后触发调度
            triggerDispatchAsync();
        }
    }

    private Duration computeBackoff(int attempts) {
        long secs = BACKOFF_MIN_SECONDS;
        for (int i = 1; i < attempts; i++) {
            secs = Math.min(BACKOFF_MAX_SECONDS, secs * BACKOFF_FACTOR);
        }
        return Duration.ofSeconds(secs);
    }

    // 重载，允许用队列中的 bizDate 覆盖默认 bizDate
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task, LocalDate overrideBizDate) {
        long tid = task.getId();
        long startTime = System.currentTimeMillis();
        try {
            tableService.setRunning(task);
            boolean result = executeEtlTaskLogic(task, overrideBizDate);
            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0);
            log.info("采集任务 {}.{}({}) 执行完成，耗时: {}s, 结果: {}", task.getSourceDb(), task.getSourceTable(), tid, duration, result);
            task.setDuration(duration);
            if (result) {
                tableService.setFinished(task);
                return TaskResultDto.success("执行成功", duration);
            } else {
                tableService.setFailed(task);
                alertService.sendToWeComRobot(String.format("采集任务执行失败: %s", tid));
                return TaskResultDto.failure("执行失败：Addax 退出非0", duration);
            }
        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("采集任务 {}.{}({}) 执行失败，耗时: {}s", task.getSourceDb(), task.getSourceTable(), tid, duration, e);
            task.setDuration(duration);
            tableService.setFailed(task);
            alertService.sendToWeComRobot(String.format("采集任务执行失败: %s, 错误: %s", tid, e.getMessage()));
            String msg = e.getMessage() == null ? "内部异常" : e.getMessage();
            return TaskResultDto.failure("执行异常: " + msg, duration);
        } finally {
            // runningTaskCount 在 submit 前已增加，在 finally 中由调用方减少
        }
    }

    // 保留旧接口，走默认 bizDate
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task) {
        return executeEtlTaskWithConcurrencyControl(task, null);
    }

    @Override
    public void truncateQueueExceptRunningTasks() {
        jobQueueService.truncateQueueExceptRunningTasks();
    }

    /**
     * 执行具体的采集逻辑，支持覆盖 bizDate
     */
    public boolean executeEtlTaskLogic(EtlTable task, LocalDate overrideBizDate) {
        long taskId = task.getId();
        log.info("执行采集任务逻辑: taskId={}, destDB={}, tableName={}", taskId, task.getTargetDb(), task.getTargetTable());
        String job = jobContentService.getJobContent(taskId);
        if (job == null || job.isEmpty()) {
            log.warn("模板未生成, taskId = {}", taskId);
            return false;
        }
        String defaultLogDate = configService.getBizDate(); // yyyyMMdd
        String dw_clt_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String partFormat = task.getPartFormat();
        String bizDateStr;
        if (overrideBizDate != null) {
            // 将 override 的 date 按 partFormat 格式化
            if (partFormat != null && !partFormat.isBlank() && !"yyyyMMdd".equals(partFormat)) {
                bizDateStr = overrideBizDate.format(DateTimeFormatter.ofPattern(partFormat));
            } else {
                bizDateStr = overrideBizDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } else {
            String bizDate = defaultLogDate;
            if (partFormat != null && !partFormat.isBlank() && !partFormat.equals("yyyyMMdd")) {
                bizDate = LocalDate.parse(defaultLogDate, DateTimeFormatter.ofPattern("yyyyMMdd")).format(DateTimeFormatter.ofPattern(partFormat));
            }
            bizDateStr = bizDate;
        }
        log.debug("biz date is {}, dw_clt_date is {}, dw_trade_date is {}", bizDateStr, dw_clt_date, defaultLogDate);
        job = job.replace("${logdate}", bizDateStr).replace("${dw_clt_date}", dw_clt_date).replace("${dw_trade_date}", defaultLogDate);
        if (task.getPartName() != null && !Objects.equals(task.getPartName(), "")) {
            boolean result = targetService.addPartition(taskId, task.getTargetDb(), task.getTargetTable(), task.getPartName(), bizDateStr);
            if (!result) {
                return false;
            }
        }
        File tempFile;
        try {
            // Determine the persistent jobs directory under the parent of program run dir
            String curDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            // The property app.home is set in the service.sh script as the parent directory of the Addax installation
            String jobsDir = Path.of(System.getProperty("app.home")).resolve("job").resolve(curDate) + "/";

            Files.createDirectories(Path.of(jobsDir));
            // Create the temp file in the persistent jobs directory
            tempFile = new File(jobsDir + task.getTargetDb() + "." + task.getTargetTable() + ".json");
            Files.writeString(tempFile.toPath(), job);
        } catch (IOException e) {
            log.error("写入临时文件失败", e);
            return false;
        }
        String logName = String.format("%s.%s_%d.log", task.getTargetDb(), task.getTargetTable(), taskId);
        String cmd = String.format("%s/bin/addax.sh  -p'-DjobName=%d -Dlog.file.name=%s' %s", dictService.getAddaxHome(), taskId, logName, tempFile.getAbsolutePath());
        boolean retCode = executeAddax(cmd, taskId, logName);
        log.debug("采集任务 {} 的日志已写入文件: {}", taskId, logName);
        return retCode;
    }

    private boolean executeAddax(String command, long tid, String logName) {
        EtlJour etlJour = jourService.addJour(tid, JourKind.COLLECT, command);
        TaskResultDto taskResult = CommandExecutor.executeWithResult(command, ADDAX_EXECUTE_TIME_OUT_SECONDS);
        Path path = Path.of(dictService.getAddaxHome() + "/log/" + logName);
        String logContent = null;
        try {
            logContent = Files.readString(path);
        } catch (IOException e) {
            log.error("读取 Addax 日志文件失败: {}", path, e);
        }
        addaxLogService.insertLog(tid, logContent);
        etlJour.setDuration(taskResult.durationSeconds());
        etlJour.setStatus(true);
        if (!taskResult.success()) {
            log.error("Addax 采集任务 {} 执行失败，退出码: {}", tid, taskResult.message());
            etlJour.setStatus(false);
            etlJour.setErrorMsg(taskResult.message());
        }
        jourService.saveJour(etlJour);
        return taskResult.success();
    }

    /**
     * 定期回收超时未续约的租约，将任务重新标记为可领取状态。
     * 回收后如果存在任务被恢复，可触发一次调度尝试。
     */
    private void recoverLeases() {
        if (!running) {
            return;
        }
        try {
            int recovered = jobQueueService.recoverExpiredLeases();
            if (recovered > 0) {
                log.info("回收过期租约 {} 个，尝试重新调度", recovered);
                // 回收了租约，说明可能有任务重新变为可执行，触发一次调度
                triggerDispatchAsync();
            }
        } catch (Exception e) {
            log.warn("租约回收任务执行异常", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        workerPool.shutdownNow();
        log.info("任务队列管理器已关闭");
    }

    // Implement interface methods or add stubs if missing
    public void stopQueueMonitor() {
        running = false;
    }

    public void restartQueueMonitor() {
        stopQueueMonitor();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        running = true;
        scheduler.execute(this::listenLoop);
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch, 1, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
    }

    public boolean addTaskToQueue(@NonNull  EtlTable etlTable) {
        if (systemFlagService != null && systemFlagService.isRefreshInProgress()) {
            log.info("当前正在更新参数/刷新表结构，拒绝将任务 {} 加入队列", etlTable.getId());
            return false;
        }
        // 拒绝在 schema 刷新期间新增任务：检查 redis 锁
        try {
            if (redisLockService != null && redisLockService.isLocked(SCHEMA_REFRESH_LOCK_KEY)) {
                log.info("当前正在刷新表结构（由 {} 控制），拒绝将任务 {} 加入队列", SCHEMA_REFRESH_LOCK_KEY, etlTable.getId());
                return false;
            }
        } catch (Exception e) {
            log.warn("检查 schema 刷新锁失败，继续按默认逻辑处理入队", e);
        }
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return jobQueueService.enqueue(etlTable, bizDate, 100) > 0;
    }

    public boolean addTaskToQueue(long tableId) {
        EtlTable table = tableService.getTable(tableId);
        return table != null && addTaskToQueue(table);
    }

    public int clearQueue() {
        return jobQueueService.clearPending();
    }

    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("pendingInDatabase", jobQueueService.countPending());
        status.put("runningTasks", runningTaskCount.get());
        return status;
    }

    public void startQueueMonitor() {
        running = true;
        scheduler.execute(this::listenLoop);
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch, 1, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
    }
}
