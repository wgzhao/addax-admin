package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集任务管理服务类，负责采集任务队列管理及相关业务操作
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService
{
    private final TaskQueueManager queueManager;
    private final TableService tableService;
    private final JdbcTemplate jdbcTemplate;
    private final EtlJourService jourService;
    private final SystemConfigService configService;

    /**
     * 执行指定采集源下的所有采集任务，将任务加入队列
     * @param sourceId 采集源ID
     */
    public void executeTasksForSource(int sourceId) {
        List<EtlTable> tables = tableService.getRunnableTasks(sourceId);
        for (EtlTable table : tables) {
            // 将采集表加入队列
            queueManager.getEtlQueue().offer(table);
        }
        log.info("Executing tasks for source {}, found {} tables", sourceId, tables.size());
    }

    /**
     * 计划任务主控 - 基于队列的采集任务管理
     * 入口方法，负责扫描tb_imp_etl表并管理采集队列
     */
    public void executePlanStartWithQueue()
    {

//        log.info("基于队列的计划任务主控制开始执行");

        // 启动队列监控器（如果还未启动）
        queueManager.startQueueMonitor();

        // 处理其他类型任务（judge等非ETL任务）
//        processNonEtlTasks();

        // 扫描tb_imp_etl表中flag字段为N的记录并加入队列
        queueManager.scanAndEnqueueEtlTasks();
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    public void updateParams()
    {
        // 在切日时间，开始重置所有采集任务的 flag 字段设置为 'N'，以便重新采集
        log.info("开始执行每日参数更新和任务重置...");
        tableService.resetAllFlags();
        // 重载系统配置
        configService.loadConfig();
    }

    /**
     * 获取采集任务队列的详细状态
     */
    public Map<String, Object> getEtlQueueStatus()
    {
        Map<String, Object> detailedStatus = new HashMap<>(queueManager.getQueueStatus());

        try {
            // 添加数据库中待处理任务数量
            int result  = tableService.findPendingTasks();
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
     * 重启队列监控
     */
    public String restartQueueMonitor()
    {
        queueManager.stopQueueMonitor();
        // 等待一下让监控线程停止
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        queueManager.startQueueMonitor();
        return "队列监控已重启";
    }

    /**
     * 清空队列并重新扫描
     */
    public String resetQueue()
    {
        int clearedCount = queueManager.clearQueue();
        queueManager.scanAndEnqueueEtlTasks();
        Map<String, Object> status = queueManager.getQueueStatus();

        return String.format("队列已重置，清空了 %d 个任务，重新扫描后队列大小: %d",
                clearedCount, status.get("queueSize"));
    }

    // 特殊任务提醒
    public List<EtlTable> findAllSpecialTask() {
        return tableService.findSpecialTasks();
    }

    // 提交采集任务到队列
    public boolean submitTask(EtlTable etlTable) {
        return queueManager.addTaskToQueue(etlTable);
    }

    public TaskResultDto submitTask(long tableId) {
        if (queueManager.addTaskToQueue(tableId) ) {
            return TaskResultDto.success("任务已提交到队列", 0);
        } else {
            return TaskResultDto.failure("任务提交失败，可能是队列已满或任务已存在", 0);
        }
    }

    public List<Map<String, Object>> getAllTaskStatus()
    {
        String sql = """
                select
                id,
                target_db || '.' ||  target_table as tbl,
                status,
                to_char(start_time, 'yyyy-MM-dd HH24:MM:SS') as start_time,
                round(case when status in ('E','W') then 0 else extract(epoch from now() - t.start_time ) / b.take_secs  end ,2) as progress
                from etl_table t
                left join
                (
                select tid,
                take_secs,
                row_number() over (partition by tid order by start_at desc) as rn
                from etl_statistic
                ) b
                on t.id = b.tid
                where rn = 1
                and t.status in ( 'R', 'W')
                order by id
                """;
        return jdbcTemplate.queryForList(sql);
    }

    public String getLastErrorByTableId(long tableId) {
        return jourService.findLastErrorByTableId(tableId);
    }
}
