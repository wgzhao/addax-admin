package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.*;
import com.wgzhao.addax.admin.service.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集任务队列管理器 - 使用 PostgreSQL 持久化队列 + LISTEN/NOTIFY
 */
@Component
@Slf4j
public class TaskQueueManagerV2Impl extends AbstractTaskQueueManager {
    @Autowired
    private StatService statService;
    @Autowired
    private SystemFlagService systemFlagService;
    @Autowired
    private EtlJobQueueService jobQueueService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 并发限制 & 入队容量
    private int concurrentLimit;
    private int enqueueCapacity;

    // 并发控制
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, AtomicInteger> sourceRunningTaskCount = new ConcurrentHashMap<>();
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

    // backoff 策略
    private static final int BACKOFF_MIN_SECONDS = 30;
    private static final int BACKOFF_MAX_SECONDS = 1800; // 30m
    private static final int BACKOFF_FACTOR = 2;

    // 轮询间隔 & 租约
    private static final int DEFAULT_POLL_INTERVAL_SECONDS = 3;
    private static final int DEFAULT_LEASE_SECONDS = 7300; // 2小时 + 5分钟 buffer,大于 ADDAX_EXECUTE_TIME_OUT_SECONDS

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
        // 启动租约回收
        scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
        log.info("DB-backed queue started. concurrentLimit={}, enqueueCapacity={}, instanceId={}", concurrentLimit, enqueueCapacity, instanceId);
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
                    TimeUnit.MILLISECONDS.sleep(2000);
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
        while (runningTaskCount.get() < concurrentLimit) {
            Optional<EtlJobQueue> maybe = jobQueueService.claimNext(instanceId, DEFAULT_LEASE_SECONDS);
            if (maybe.isEmpty()) {
                return;
            }
            EtlJobQueue job = maybe.get();
            // 获取任务对应的数据源信息
            EtlTable table = tableService.getTable(job.getTid());
            if (table == null) {
                log.warn("未找到 tid={} 对应��任务信息，跳过该任务", job.getTid());
                jobQueueService.completeFailure(job.getId(), "任务信息丢失");
                continue;
            }
            VwEtlTableWithSource source = tableService.getTableView(table.getId());
            Integer maxConcurrency = source.getMaxConcurrency();

            // 检查数据源并发
            if (maxConcurrency != null && maxConcurrency > 0) {
                AtomicInteger sourceCount = sourceRunningTaskCount.computeIfAbsent(table.getSid(), k -> new AtomicInteger(0));
                if (sourceCount.get() >= maxConcurrency) {
                    log.info("数据源 {}({}) 并发已达上限 {}，任务 jobId={} 将被放回队列", source.getName(), source.getCode(), maxConcurrency, job.getId());
                    // 并发已满，放回队列
                    jobQueueService.releaseClaim(job.getId());
                    // 稍微等待，避免立即再次获取到同一个任务
                    try {
                        TimeUnit.MILLISECONDS.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    continue; // 继续尝试下一个任务
                }
            }

            int current = runningTaskCount.incrementAndGet();
            // 如果需要，增加数据源并发计数
            if (maxConcurrency != null && maxConcurrency > 0) {
                sourceRunningTaskCount.get(table.getSid()).incrementAndGet();
            }
            log.info("领取任务 jobId={}, tid={}, attempts={}/{}, 当前并发 {}/{}, 数据源 {} 并发 {}/{}",
                    job.getId(), job.getTid(), job.getAttempts(), job.getMaxAttempts(),
                    current, concurrentLimit, source.getCode(),
                    sourceRunningTaskCount.getOrDefault(table.getSid(), new AtomicInteger(0)).get(),
                    maxConcurrency == null || maxConcurrency <= 0 ? "无限制" : maxConcurrency);
            workerPool.submit(() -> executeClaimedJob(job));
        }
    }

    private void recoverLeases() {
        try {
            int n = jobQueueService.recoverExpiredLeases();
            if (n > 0) {
                log.info("回收过期租约 {} 条", n);
            }
        } catch (Exception e) {
            log.warn("回收租约失败", e);
        }
    }

    private void executeClaimedJob(EtlJobQueue job) {
        long start = System.currentTimeMillis();
        // Schedule periodic lease renewal (heartbeat) while this job runs
        ScheduledFuture<?> renewer = null;
        try {
            int renewInterval = Math.max(30, DEFAULT_LEASE_SECONDS / 3);
            renewer = scheduler.scheduleAtFixedRate(() -> {
                try {
                    boolean renewed = jobQueueService.renewLease(job.getId(), instanceId, DEFAULT_LEASE_SECONDS);
                    if (!renewed) {
                        log.warn("租约续期失败 jobId={}, instanceId={}，该任务可能已被其它实例接管", job.getId(), instanceId);
                    } else {
                        log.debug("租约续期成功 jobId={} by {}", job.getId(), instanceId);
                    }
                } catch (Exception e) {
                    log.warn("租约续期异常 jobId={}", job.getId(), e);
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
            // 释放并发计数
            int current = runningTaskCount.decrementAndGet();
            // 释放数据源并发计数
            EtlTable table = tableService.getTable(job.getTid());
            if (table != null) {
                VwEtlTableWithSource source = tableService.getTableView(table.getId());
                if (source != null) {
                    Integer maxConcurrency = source.getMaxConcurrency();
                    if (maxConcurrency != null && maxConcurrency > 0) {
                        sourceRunningTaskCount.computeIfPresent(table.getSid(), (k, v) -> {
                            v.decrementAndGet();
                            return v;
                        });
                    }
                }
            }
            log.debug("jobId={} 完成，耗时 {}s，当前并发 {}", job.getId(), (System.currentTimeMillis() - start) / 1000, current);
        }
    }

    private Duration computeBackoff(int attempts) {
        long secs = BACKOFF_MIN_SECONDS;
        for (int i = 1; i < attempts; i++) {
            secs = Math.min((long) BACKOFF_MAX_SECONDS, secs * BACKOFF_FACTOR);
        }
        return Duration.ofSeconds(secs);
    }

    // 保留旧接口，走默认 bizDate
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task) {
        return executeEtlTaskWithConcurrencyControl(task, null);
    }

    @Override
    public void truncateQueueExceptRunningTasks() {
        jobQueueService.truncateQueueExceptRunningTasks();
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
            TimeUnit.MILLISECONDS.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        running = true;
        scheduler.execute(this::listenLoop);
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch, 1, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
    }

    public boolean addTaskToQueue(EtlTable etlTable) {
        if (systemFlagService != null && systemFlagService.isRefreshInProgress()) {
            log.info("当前正在更新参数/刷新表结构，拒绝将任务 {} 加入队列", etlTable == null ? "null" : etlTable.getId());
            return false;
        }
        LocalDate bizDate = LocalDate.parse(configService.getBizDate(), java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return jobQueueService.enqueue(etlTable, bizDate, 100) > 0;
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

    @Override
    public void close() {
        shutdown();
    }
}
