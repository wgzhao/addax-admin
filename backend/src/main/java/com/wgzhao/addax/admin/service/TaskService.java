package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.redis.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采集任务管理服务类，负责采集任务队列管理及相关业务操作
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService
{
    private static final Pattern INSTANCE_WITH_PID_PATTERN = Pattern.compile("^(.*)-\\d+@.*$");

    private final TaskQueueManager queueManager;
    private final TableService tableService;
    private final JdbcTemplate jdbcTemplate;
    private final EtlJourService jourService;
    private final EtlJobQueueService jobQueueService;
    private final SystemConfigService configService;
    private final RedisLockService redisLockService;
    private final ExecutionManager executionManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 执行指定采集源下的所有采集任务，将任务加入队列
     * 注意：近将那些表字段 start_at 为 null 的表加入队列，避免重复采集。
     *
     * @param sourceId 采集源 ID
     */
    public void executeTasksForSource(int sourceId)
    {
        List<EtlTable> tables = tableService.getRunnableInheritedTasksBySource(sourceId);
        for (EtlTable table : tables) {
            // 将采集表加入队列
            queueManager.addTaskToQueue(table);
        }
        log.info("Executing tasks for source {}, found {} tables", sourceId, tables.size());
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    public void updateParams()
    {
        // 在切日时间，开始重置所有采集任务的 flag 字段设置为 'N'，以便重新采集
        log.info("开始执行每日参数更新和任务重置...");

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "updateParams-worker");
            t.setDaemon(true);
            return t;
        });

        Future<?> future = null;
        try {
            log.info("已获取 schema refresh 锁：正在更新参数与刷新表结构，期间新任务将被拒绝或不被入队。已经在执行的任务不受影响。");

            // Stop the queue monitor to prevent new queued tasks from being dispatched while we refresh.
            // Note: running tasks are not interrupted.
            queueManager.stopQueueMonitor();

            // Submit refresh job and wait with timeout
            future = executor.submit(() -> {
                try {
                    // Reset flags so tasks become eligible after refresh
                    tableService.resetAllFlags();
                    // Reload system configuration
                    configService.loadConfig();

                    // Refresh schema / resources for all tables. This checks source schema and updates target metadata when changed.
                    tableService.refreshAllTableResources();

                    // truncate the job queue table except for running tasks
                    // 106 行注释：在完成必要参数配置及初始化后，需要清理 etl_job_queue 中的历史记录
                    // 这里只保留仍在运行中的任务，删除 completed/failed 等状态的记录，减轻后续查询压力
                    queueManager.truncateQueueExceptRunningTasks();
                }
                catch (Exception e) {
                    // Let outer handler deal with logging
                    throw new RuntimeException(e);
                }
            });

            try {
                int timeout = configService.getSchemaRefreshTimeoutSeconds();
                future.get(timeout, TimeUnit.SECONDS);
                log.info("参数更新与表结构刷新完成");
            }
            catch (TimeoutException te) {
                int timeout = configService.getSchemaRefreshTimeoutSeconds();
                log.error("参数更新/表结构刷新超时（>{}s），将中止刷新并释放锁", timeout);
                // try to cancel the running task
                future.cancel(true);
            }
            catch (ExecutionException ee) {
                log.error("参数更新/表结构刷新发生异常", ee.getCause());
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("updateParams 被中断");
            }
        }
        catch (Exception e) {
            log.error("更新参数或刷新表结构过程中发生错误", e);
        }
        finally {
            // Always restart the queue monitor and release the lock if we acquired it
            try {
                queueManager.startQueueMonitor();
            }
            catch (Exception e) {
                log.warn("重启队列监控器时发生错误", e);
            }

            log.info("已释放 schema refresh 锁，队列监控器已重启，采集任务恢复正常");

            if (future != null && !future.isDone()) {
                try {
                    future.cancel(true);
                }
                catch (Exception ignored) {
                }
            }
            executor.shutdownNow();
        }
    }

    /**
     * 获取采集任务队列的详细状态
     */
    public Map<String, Object> getEtlQueueStatus()
    {
        Map<String, Object> detailedStatus = new HashMap<>(queueManager.getQueueStatus());

        try {
            // 添加数据库中待处理任务数量
            int result = tableService.findPendingTasks();
            if (result > 0) {
                detailedStatus.put("pendingInDatabase", result);
            }
            result = tableService.findRunningTasks();
            if (result > 0) {
                detailedStatus.put("runningInDatabase", result);
            }
        }
        catch (Exception e) {
            log.error("获取数据库任务状态失败", e);
        }

        return detailedStatus;
    }

    /**
     * 停止队列监控
     */
    public String stopQueueMonitor()
    {
        queueManager.stopQueueMonitor();
        return "队列监控停止信号已发送";
    }

    /**
     * 启动队列监控
     */
    public String startQueueMonitor()
    {
        queueManager.startQueueMonitor();
        return "队列监控已启动";
    }

    /**
     * 清空队列并重新扫描
     */
    public String resetQueue()
    {
        int clearedCount = queueManager.clearQueue();
        queueManager.scanAndEnqueueEtlTasks();
        Map<String, Object> status = queueManager.getQueueStatus();

        return String.format("队列已重置，清空了 %d 个任务，重新扫描后队列大小: %s",
            clearedCount, status.get("queueSize"));
    }

    // 特殊任务提醒
    public List<EtlTable> findAllSpecialTask()
    {
        return tableService.findSpecialTasks();
    }

    public TaskResultDto submitTask(long tableId)
    {
        // 如果 schema 刷新中（由 redis 锁控制），拒绝提交
        try {
            if (redisLockService != null && redisLockService.isRefreshInProgress()) {
                log.info("当前正在更新参数/刷新表结构，拒绝直接提交任务：tableId={}", tableId);
                return TaskResultDto.failure("正在刷新表结构，暂时无法提交任务", 0);
            }
        }
        catch (Exception e) {
            log.warn("检查 schema 刷新锁失败，继续按 DB flag 逻辑处理", e);
        }
        if (queueManager.addTaskToQueue(tableId)) {
            return TaskResultDto.success("任务已提交到队列", 0);
        }
        else {
            return TaskResultDto.failure("任务提交失败，可能是队列已满或任务已存在", 0);
        }
    }

    public TaskResultDto submitTask(long tableId, String username)
    {
        // 如果 schema 刷新中（由 redis 锁控制），拒绝提交
        try {
            if (redisLockService != null && redisLockService.isRefreshInProgress()) {
                log.info("当前正在更新参数/刷新表结构，拒绝直接提交任务：tableId={}", tableId);
                return TaskResultDto.failure("正在刷新表结构，暂时无法提交任务", 0);
            }
        }
        catch (Exception e) {
            log.warn("检查 schema 刷新锁失败，继续按 DB flag 逻辑处理", e);
        }

        String payload = null;
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("submitter", username);
            map.put("action", "collect");
            payload = objectMapper.writeValueAsString(map);
        }
        catch (Exception e) {
            log.debug("Failed to serialize task payload for tid={}", tableId);
        }

        if (queueManager.addTaskToQueue(tableId, payload)) {
            return TaskResultDto.success("任务已提交到队列", 0);
        }
        else {
            return TaskResultDto.failure("任务提交失败，可能是队列已满或任务已存在", 0);
        }
    }

    /**
     * Kill a running task by table id. If running on this instance, kill locally; otherwise publish a Redis kill message.
     * 
     * @param tid table id
     * @param manualKill true if kill is triggered by user (front-end), false if by system (e.g., timeout)
     *                    when true, will cleanup job queue and reset retry_cnt to prevent re-triggering
     */
    public TaskResultDto killTask(long tid, boolean manualKill)
    {
        try {
            // try local kill first
            boolean killedLocal = executionManager.killLocal(tid);
            EtlJour etlJour = jourService.getLastByTidWithKind(tid, null);
            if (killedLocal) {
                log.warn("Killed local collecting table {} by request (manualKill={})", tid, manualKill);
                try {
                    String reason = manualKill ? "Killed by user request" : "Killed by system (timeout/other)";
                    jourService.failJour(etlJour, reason);
                }
                catch (Exception e) {
                    log.warn("Failed to record kill reason to etl_jour for table {}", tid, e);
                }
                
                // If manual kill, clean up job queue and reset retry count to prevent re-triggering
                if (manualKill) {
                    cleanupJobQueueForManualKill(tid);
                }
                
                return TaskResultDto.success("Killed local collecting job", 0);
            }

            // publish kill to redis channel for remote nodes to handle
            String channel = "etl:kill";
            String payload = String.valueOf(tid);
            try {
                stringRedisTemplate.convertAndSend(channel, payload);
                // set fallback signal key for a short period so target node can detect if pub/sub missed
                String signalKey = "etl:kill:signal:" + tid;
                stringRedisTemplate.opsForValue().set(signalKey, "1", java.time.Duration.ofSeconds(30));
                log.info("Published kill request for job {} to channel {}", tid, channel);
                try {
                    String reason = manualKill ? "Kill requested by user (remote)" : "Kill requested by system (remote)";
                    jourService.failJour(etlJour, reason);
                }
                catch (Exception e) {
                    log.warn("Failed to record kill request to etl_jour for job {}", tid, e);
                }
                
                // If manual kill, clean up job queue and reset retry count
                if (manualKill) {
                    cleanupJobQueueForManualKill(tid);
                }
                
                return TaskResultDto.success("Kill request published", 0);
            }
            catch (Exception e) {
                log.error("Failed to publish kill request for job {}", tid, e);
                return TaskResultDto.failure("Failed to publish kill request: " + e.getMessage(), 0);
            }
        }
        catch (Exception e) {
            log.error("killTask failed for job {}", tid, e);
            return TaskResultDto.failure(e.getMessage() == null ? "internal error" : e.getMessage(), 0);
        }
    }

    /**
     * Overloaded method for backward compatibility.
     * Defaults to manual kill (user-triggered) when called without manualKill parameter.
     */
    public TaskResultDto killTask(long tid)
    {
        return killTask(tid, true);
    }

    /**
     * Clean up job queue for manual kill: cancel all pending/running jobs and reset retry count.
     * 
     * why: User manual kill means problem discovered, retry won't help.
     * So we should prevent scheduler from re-queueing and queue from retrying.
     */
    private void cleanupJobQueueForManualKill(long tid)
    {
        try {
            // Find all pending and running jobs for this table
            List<EtlJobQueue> jobs = new ArrayList<>();
            jobs.addAll(jobQueueService.findByTidAndStatus(tid, "pending"));
            jobs.addAll(jobQueueService.findByTidAndStatus(tid, "running"));
            
            // Cancel all of them
            for (EtlJobQueue job : jobs) {
                try {
                    jobQueueService.completeFailure(job.getId(), "Cancelled by manual kill request");
                    log.info("Cancelled job {} (tid={}) due to manual kill", job.getId(), tid);
                }
                catch (Exception e) {
                    log.warn("Failed to cancel job {} for tid={}", job.getId(), tid, e);
                }
            }
            
            // Update etl_table: set status to COLLECT_FAIL and retry_cnt to 0
            // why: prevent scheduler from re-queueing this table and queue from retrying
            EtlTable table = tableService.getTable(tid);
            if (table != null) {
                table.setStatus(TableStatus.COLLECT_FAIL);  // E
                table.setRetryCnt(0);  // forbid any retry
                table.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()));
                tableService.save(table);
                log.info("Set table {} status to FAIL with retry_cnt=0 due to manual kill", tid);
            }
        }
        catch (Exception e) {
            log.error("Failed to cleanup job queue for manual kill (tid={})", tid, e);
            // don't throw exception: process already killed, queue cleanup failure should not affect main flow
        }
    }

    public List<Map<String, Object>> getAllTaskStatus()
    {
        // 第一次采集时的估算默认耗时（秒），可按需调整或从配置读取
        int defaultTakeSecs = 300;
        String sql = """
            select
            id,
            target_db || '.' ||  target_table as tbl,
            status,
            to_char(start_time, 'yyyy-MM-dd HH24:MM:SS') as start_time,
            q.claimed_by,
            least(
            100,
            round(
            case when status in ('E','W') then 0
                else  extract(epoch from now() - t.start_time) * 100.0 / COALESCE(b.take_secs, ?)
            end ,0
            )
            )::int
            as progress
            from etl_table t
            left join
            (
            select tid,
            take_secs,
            row_number() over (partition by tid order by start_at desc) as rn
            from etl_statistic
            ) b
            on t.id = b.tid and t.status in ( 'R', 'W')
            left join
            (
            select tid,
            claimed_by,
            row_number() over (partition by tid order by claimed_at desc, id desc) as qrn
            from etl_job_queue
            where status = 'running'
            ) q
            on t.id = q.tid and q.qrn = 1
            where rn = 1
            order by id
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, defaultTakeSecs);
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("id");
            if (!(idObj instanceof Number num)) {
                row.put("node_name", "");
                continue;
            }
            long tid = num.longValue();
            String instanceId = row.get("claimed_by") == null ? "" : String.valueOf(row.get("claimed_by"));
            if (instanceId.isBlank()) {
                instanceId = executionManager.findInstanceId(tid).orElse("");
            }
            row.put("node_name", extractNodeName(instanceId));
            row.remove("claimed_by");
        }
        return rows;
    }

    private String extractNodeName(String instanceId)
    {
        if (instanceId == null || instanceId.isBlank()) {
            return "";
        }
        Matcher matcher = INSTANCE_WITH_PID_PATTERN.matcher(instanceId);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        int idx = instanceId.lastIndexOf('-');
        return idx > 0 ? instanceId.substring(0, idx) : instanceId;
    }
}
