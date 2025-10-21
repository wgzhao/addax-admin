package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.service.*;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;

/**
 * 采集任务队列管理器 - 使用 PostgreSQL 持久化队列 + LISTEN/NOTIFY
 */
@Component
@Primary
@Slf4j
public class TaskQueueManagerV2Impl implements TaskQueueManager
{
    @Autowired private DictService dictService;
    @Autowired private AddaxLogService addaxLogService;
    @Autowired private StatService statService;
    @Autowired private AlertService alertService;
    @Autowired private TableService tableService;
    @Autowired private EtlJourService jourService;
    @Autowired private SystemConfigService configService;
    @Autowired private JobContentService jobContentService;
    @Autowired private TargetService targetService;
    @Autowired private EtlJobQueueService jobQueueService;
    @Autowired private JdbcTemplate jdbcTemplate;

    // 并发限制 & 入队容量
    private int concurrentLimit;
    private int enqueueCapacity;

    // 并发控制
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);
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
    private static final int DEFAULT_LEASE_SECONDS = 600;

    @PostConstruct
    public void init()
    {
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

    private String resolveInstanceId()
    {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String pid = ManagementFactory.getRuntimeMXBean().getName();
            return host + "-" + pid;
        }
        catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 扫描并入队（DB 持久化），遵守容量限制与去重
     */
    public void scanAndEnqueueEtlTasks()
    {
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
        }
        catch (Exception e) {
            log.error("扫描并入队失败", e);
            alertService.sendToWeComRobot("扫描采集任务失败: " + e.getMessage());
        }
    }

    private void listenLoop()
    {
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
                    Thread.sleep(500L);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (SQLException se) {
                    log.warn("LISTEN 循环 SQL 异常，将重试", se);
                    Thread.sleep(2000);
                }
            }
        }
        catch (Exception e) {
            log.error("LISTEN 监听器异常退出", e);
        }
        log.info("LISTEN 监听器结束");
    }

    private void pollAndDispatch()
    {
        if (!running) return;
        dispatchUntilFull();
    }

    private void dispatchUntilFull()
    {
        while (runningTaskCount.get() < concurrentLimit) {
            Optional<EtlJobQueue> maybe = jobQueueService.claimNext(instanceId, DEFAULT_LEASE_SECONDS);
            if (maybe.isEmpty()) {
                return;
            }
            EtlJobQueue job = maybe.get();
            int current = runningTaskCount.incrementAndGet();
            log.info("领取任务 jobId={}, tid={}, attempts={}/{}, 当前并发 {}/{}", job.getId(), job.getTid(), job.getAttempts(), job.getMaxAttempts(), current, concurrentLimit);
            workerPool.submit(() -> executeClaimedJob(job));
        }
    }

    private void recoverLeases()
    {
        try {
            int n = jobQueueService.recoverExpiredLeases();
            if (n > 0) {
                log.info("回收过期租约 {} 条", n);
            }
        }
        catch (Exception e) {
            log.warn("回收租约失败", e);
        }
    }

    private void executeClaimedJob(EtlJobQueue job)
    {
        long start = System.currentTimeMillis();
        try {
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
        }
        catch (Exception e) {
            log.error("执行任务失败 jobId={} tid={}", job.getId(), job.getTid(), e);
            Duration backoff = computeBackoff(job.getAttempts());
            try {
                jobQueueService.failOrReschedule(job, e.getMessage(), backoff);
            } catch (Exception ignored) { }
        }
        finally {
            int current = runningTaskCount.decrementAndGet();
            log.debug("jobId={} 完成，耗时 {}s，当前并发 {}", job.getId(), (System.currentTimeMillis()-start)/1000, current);
        }
    }

    private Duration computeBackoff(int attempts)
    {
        long secs = BACKOFF_MIN_SECONDS;
        for (int i = 1; i < attempts; i++) {
            secs = Math.min((long) BACKOFF_MAX_SECONDS, secs * BACKOFF_FACTOR);
        }
        return Duration.ofSeconds(secs);
    }

    // 重载，允许用队列中的 bizDate 覆盖默认 bizDate
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task, LocalDate overrideBizDate)
    {
        long tid = task.getId();
        long startTime = System.currentTimeMillis();
        try {
            tableService.setRunning(task);
            boolean result = executeEtlTaskLogic(task, overrideBizDate);
            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0);
            log.info("采集任务 {} 执行完成，耗时: {}s, 结果: {}", tid, duration, result);
            task.setDuration(duration);
            if (result) {
                tableService.setFinished(task);
                return TaskResultDto.success("执行成功", duration);
            } else {
                tableService.setFailed(task);
                alertService.sendToWeComRobot(String.format("采集任务执行失败: %s", tid));
                return TaskResultDto.failure("执行失败：Addax 退出非0", duration);
            }
        }
        catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("采集任务 {} 执行失败，耗时: {}s", tid, duration, e);
            task.setDuration(duration);
            tableService.setFailed(task);
            alertService.sendToWeComRobot(String.format("采集任务执行失败: %s, 错误: %s", tid, e.getMessage()));
            String msg = e.getMessage() == null ? "内部异常" : e.getMessage();
            return TaskResultDto.failure("执行异常: " + msg, duration);
        }
        finally {
            // runningTaskCount 在 submit 前已增加，在 finally 中由调用方减少
        }
    }

    // 保留旧接口，走默认 bizDate
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task)
    {
        return executeEtlTaskWithConcurrencyControl(task, null);
    }

    /**
     * 执行具体的采集逻辑，支持覆盖 bizDate
     */
    public boolean executeEtlTaskLogic(EtlTable task, LocalDate overrideBizDate)
    {
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
        log.info("biz date is {}, dw_clt_date is {}, dw_trade_date is {}", bizDateStr, dw_clt_date, defaultLogDate);
        job = job.replace("${logdate}", bizDateStr).replace("${dw_clt_date}", dw_clt_date).replace("${dw_trade_date}", defaultLogDate);
        if (task.getPartName() != null && !Objects.equals(task.getPartName(), "")) {
            boolean result = targetService.addPartition(taskId, task.getTargetDb(), task.getTargetTable(), task.getPartName(), bizDateStr);
            if (!result) {
                return false;
            }
        }
        File tempFile;
        try {
            tempFile = File.createTempFile(task.getTargetDb() + "." + task.getTargetTable() + "_", ".json");
            Files.writeString(tempFile.toPath(), job);
        }
        catch (IOException e) {
            log.error("写入临时文件失败", e);
            return false;
        }
        String logName = String.format("addax_%s_%d.log", taskId, System.currentTimeMillis());
        String cmd = String.format("%s/bin/addax.sh  -p'-DjobName=%d -Dlog.file.name=%s' %s", dictService.getAddaxHome(), taskId, logName, tempFile.getAbsolutePath());
        boolean retCode = executeAddax(cmd, taskId, logName);
        log.info("采集任务 {} 的日志已写入文件: {}", taskId, logName);
        return retCode;
    }

    private boolean executeAddax(String command, long tid, String logName)
    {
        log.info("Executing command: {}", command);
        EtlJour etlJour = jourService.addJour(tid, JourKind.COLLECT, command);
        TaskResultDto taskResult = CommandExecutor.executeWithResult(command);
        Path path = Path.of(dictService.getAddaxHome() + "/log/" + logName);
        try {
            String logContent = Files.readString(path);
            addaxLogService.insertLog(tid, logContent);
        }
        catch (IOException e) {
            log.warn("Failed to get the addax log content: {}", path);
        }
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

    @PreDestroy
    public void shutdown()
    {
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
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        running = true;
        scheduler.execute(this::listenLoop);
        scheduler.scheduleWithFixedDelay(this::pollAndDispatch, 1, DEFAULT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::recoverLeases, 30, 30, TimeUnit.SECONDS);
    }
    public boolean addTaskToQueue(EtlTable etlTable) {
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
