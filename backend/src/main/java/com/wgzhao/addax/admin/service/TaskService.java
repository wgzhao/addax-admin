package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.wgzhao.addax.admin.common.Constants.SCHEMA_REFRESH_LOCK_KEY;

/**
 * 采集任务管理服务类，负责采集任务队列管理及相关业务操作
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {
    private final TaskQueueManager queueManager;
    private final TableService tableService;
    private final JdbcTemplate jdbcTemplate;
    private final EtlJourService jourService;
    private final SystemConfigService configService;
    private final SystemFlagService systemFlagService; // added dependency
    private final com.wgzhao.addax.admin.redis.RedisLockService redisLockService;
    private final com.wgzhao.addax.admin.service.ExecutionManager executionManager;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /**
     * 执行指定采集源下的所有采集任务，将任务加入队列
     *
     * @param sourceId 采集源ID
     */
    public void executeTasksForSource(int sourceId) {
        List<EtlTable> tables = tableService.getRunnableTasks(sourceId);
        for (EtlTable table : tables) {
            // 将采集表加入队列
            queueManager.addTaskToQueue(table);
            // V1
            // queueManager.getEtlQueue().offer(table);
        }
        log.info("Executing tasks for source {}, found {} tables", sourceId, tables.size());
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    public void updateParams() {
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
                } catch (Exception e) {
                    // Let outer handler deal with logging
                    throw new RuntimeException(e);
                }
            });

            try {
                int timeout = configService.getSchemaRefreshTimeoutSeconds();
                future.get(timeout, TimeUnit.SECONDS);
                log.info("参数更新与表结构刷新完成");
            } catch (TimeoutException te) {
                int timeout = configService.getSchemaRefreshTimeoutSeconds();
                log.error("参数更新/表结构刷新超时（>{}s），将中止刷新并释放锁", timeout);
                // try to cancel the running task
                future.cancel(true);
            } catch (ExecutionException ee) {
                log.error("参数更新/表结构刷新发生异常", ee.getCause());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("updateParams 被中断");
            }
        } catch (Exception e) {
            log.error("更新参数或刷新表结构过程中发生错误", e);
        } finally {
            // Always restart the queue monitor and release the lock if we acquired it
            try {
                queueManager.startQueueMonitor();
            } catch (Exception e) {
                log.warn("重启队列监控器时发生错误", e);
            }

            systemFlagService.endRefresh("updateParams");
            log.info("已释放 schema refresh 锁，队列监控器已重启，采集任务恢复正常");

            if (future != null && !future.isDone()) {
                try {
                    future.cancel(true);
                } catch (Exception ignored) {
                }
            }
            executor.shutdownNow();
        }
    }

    /**
     * 获取采集任务队列的详细状态
     */
    public Map<String, Object> getEtlQueueStatus() {
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
        } catch (Exception e) {
            log.error("获取数据库任务状态失败", e);
        }

        return detailedStatus;
    }

    /**
     * 停止队列监控
     */
    public String stopQueueMonitor() {
        queueManager.stopQueueMonitor();
        return "队列监控停止信号已发送";
    }

    /**
     * 启动队列监控
     */
    public String startQueueMonitor() {
        queueManager.startQueueMonitor();
        return "队列监控已启动";
    }

    /**
     * 清空队列并重新扫描
     */
    public String resetQueue() {
        int clearedCount = queueManager.clearQueue();
        queueManager.scanAndEnqueueEtlTasks();
        Map<String, Object> status = queueManager.getQueueStatus();

        return String.format("队列已重置，清空了 %d 个任务，重新扫描后队列大小: %s",
                clearedCount, status.get("queueSize"));
    }

    // 特殊任务提醒
    public List<EtlTable> findAllSpecialTask() {
        return tableService.findSpecialTasks();
    }

    public TaskResultDto submitTask(long tableId) {
        // 如果 schema 刷新中（由 redis 锁控制），拒绝提交
        try {
            if (redisLockService != null && redisLockService.isLocked(com.wgzhao.addax.admin.common.Constants.SCHEMA_REFRESH_LOCK_KEY)) {
                log.info("当前正在刷新表结构（由 {} 控制），拒绝直接提交任务：tableId={}", com.wgzhao.addax.admin.common.Constants.SCHEMA_REFRESH_LOCK_KEY, tableId);
                return TaskResultDto.failure("正在刷新表结构，暂时无法提交任务", 0);
            }
        } catch (Exception e) {
            log.warn("检查 schema 刷新锁失败，继续按 DB flag 逻辑处理", e);
        }
        if (systemFlagService.isRefreshInProgress()) {
            log.info("当前正在更新参数/刷新表结构，暂时拒绝提交采集任务：tableId={}", tableId);
            return TaskResultDto.failure("正在更新参数，暂时无法提交任务", 0);
        }
        if (queueManager.addTaskToQueue(tableId)) {
            return TaskResultDto.success("任务已提交到队列", 0);
        } else {
            return TaskResultDto.failure("任务提交失败，可能是队列已满或任务已存在", 0);
        }
    }

    /**
     * Kill a running task by job id. If running on this instance, kill locally; otherwise publish a Redis kill message.
     */
    public TaskResultDto killTask(long jobId) {
        try {
            // try local kill first
            boolean killedLocal = executionManager.killLocal(jobId);
            EtlJour etlJour =  jourService.getLastByTidWithKind(jobId, null);
            if (killedLocal) {
                log.warn("Killed local job {} by request", jobId);
                try {
                    jourService.failJour(etlJour, "Killed by user request");
                } catch (Exception e) {
                    log.warn("Failed to record kill reason to etl_jour for job {}", jobId, e);
                }
                return TaskResultDto.success("Killed local job", 0);
            }

            // publish kill to redis channel for remote nodes to handle
            String channel = "etl:kill";
            String payload = String.valueOf(jobId);
            try {
                stringRedisTemplate.convertAndSend(channel, payload);
                // set fallback signal key for a short period so target node can detect if pub/sub missed
                String signalKey = "etl:kill:signal:" + jobId;
                stringRedisTemplate.opsForValue().set(signalKey, "1", java.time.Duration.ofSeconds(30));
                log.info("Published kill request for job {} to channel {}", jobId, channel);
                try {
                    jourService.failJour(etlJour, "Kill requested by user (remote)");
                } catch (Exception e) {
                    log.warn("Failed to record kill request to etl_jour for job {}", jobId, e);
                }
                return TaskResultDto.success("Kill request published", 0);
            } catch (Exception e) {
                log.error("Failed to publish kill request for job {}", jobId, e);
                return TaskResultDto.failure("Failed to publish kill request: " + e.getMessage(), 0);
            }
        } catch (Exception e) {
            log.error("killTask failed for job {}", jobId, e);
            return TaskResultDto.failure(e.getMessage() == null ? "internal error" : e.getMessage(), 0);
        }
    }

    public List<Map<String, Object>> getAllTaskStatus() {
        // 第一次采集时的估算默认耗时（秒），可按需调整或从配置读取
        int defaultTakeSecs = 300;
        String sql = """
                select
                id,
                target_db || '.' ||  target_table as tbl,
                status,
                to_char(start_time, 'yyyy-MM-dd HH24:MM:SS') as start_time,
                round(
                case when status in ('E','W') then 0
                    else  extract(epoch from now() - t.start_time) * 1.0 / COALESCE(b.take_secs, ?)
                end ,2)
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
                where rn = 1
                order by id
                """;
        return jdbcTemplate.queryForList(sql, defaultTakeSecs);
    }

    public String getLastErrorByTableId(long tableId) {
        return jourService.findLastErrorByTableId(tableId);
    }
}
