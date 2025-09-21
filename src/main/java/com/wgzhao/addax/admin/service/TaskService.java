package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集任务入口管理器
 * 提供修改后的executePlanStart方法，集成队列管理功能
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService
{

    private final TaskQueueManager queueManager;

    private final SystemConfigService configService;
    private final TableService tableService;

    /**
     * 计划任务主控制 - 基于队列的采集任务管理
     * 这是采集任务的入口方法，负责扫描tb_imp_etl表并管理采集队列
     */
    public void executePlanStartWithQueue()
    {

//        log.info("基于队列的计划任务主控制开始执行");

        // 启动队列监控器（如果还未启动）
        queueManager.startQueueMonitor();

        // 处理其他类型任务（judge等非ETL任务）
        processNonEtlTasks();

        // 扫描tb_imp_etl表中flag字段为N的记录并加入队列
        queueManager.scanAndEnqueueEtlTasks();
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    private void processNonEtlTasks()
    {
        // 如果当前时间是在切日时间附近，则开始做切日处理
        // 1. 把所有有效的采集任务的 flag 字段设置为 'N'，以便重新采集
        String currTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String switchTime = configService.getSwitchTime();
        // 只需要比较分钟和小时即可
        if (currTime.equals(switchTime)) {
            log.info("当前时间 {} 在切日时间 {} 附近，开始重置所有采集任务的 flag 字段为 'N'", currTime, switchTime);
            tableService.resetAllFlags();
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

//    /** 参数更新 */
//    public String updateParameters() {
//        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
//        if (!"1630".equals(now)) { recordSystemLog("参数更新任务不能执行,任务退出,非切日时间点"); return "非切日时间点"; }
//        sendToWecomRobot("系统参数param_sys开始切换\nTD=" + executeRedisCommand("get param.TD") + "\nCD=" + executeRedisCommand("get param.CD"));
//        List<String> tds = List.of(
//                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
//                LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")),
//                LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
//        );
//        for (String td : tds) {
//            try { executeSqlStatement("select sp_imp_param(" + td + ")"); }
//            catch (Exception e) { sendToWecomRobot("系统参数param_sys生成失败!!!!"); return "失败"; }
//        }
//        List<String> rdsCmds = querySingleColumn("select rds from vw_updt_rds");
//        for (String line : rdsCmds) log.info("更新redis: {} => {}", line, executeRedisCommand(line));
//        sendToWecomRobot("系统参数param_sys切换完成！\n TD=" + executeRedisCommand("get param.TD") + "\n CD=" + executeRedisCommand("get param.CD"));
//        recordSystemLog("参数更新任务执行完毕");
//        return "参数更新完成";
//    }

    // 特殊任务提醒
    public List<EtlTable> findAllSpecialTask() {
        return tableService.findSpecialTasks();
    }
}
