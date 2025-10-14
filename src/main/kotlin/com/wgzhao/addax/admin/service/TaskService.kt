package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.success
import com.wgzhao.addax.admin.model.EtlTable
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * 采集任务管理服务类，负责采集任务队列管理及相关业务操作
 */
@Service
class TaskService(
    private val queueManager: TaskQueueManager,
    private val tableService: TableService,
    private val jdbcTemplate: JdbcTemplate,
    private val jourService: EtlJourService,
    private val configService: SystemConfigService
) {
    private val log = LoggerFactory.getLogger(TaskService::class.java)

    /**
     * 执行指定采集源下的所有采集任务，将任务加入队列
     * @param sourceId 采集源ID
     */
    fun executeTasksForSource(sourceId: Int) {
        val tables = tableService.getRunnableTasks(sourceId)
        tables.forEach { queueManager.getEtlQueue().offer(it) }
        log.info("Executing tasks for source {}, found {} tables", sourceId, tables.size)
    }

    /**
     * 计划任务主控 - 基于队列的采集任务管理
     * 入口方法，负责扫描tb_imp_etl表并管理采集队列
     */
    fun executePlanStartWithQueue() {
        //        log.info("基于队列的计划任务主控制开始执行");

        // 启动队列监控器（如果还未启动）

        queueManager.startQueueMonitor()

        // 处理其他类型任务（judge等非ETL任务）
//        processNonEtlTasks();

        // 扫描tb_imp_etl表中flag字段为N的记录并加入队列
        queueManager.scanAndEnqueueEtlTasks()
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    fun updateParams() {
        // 在切日时间，开始重置所有采集任务的 flag 字段设置为 'N'，以便重新采集
        log.info("开始执行每日参数更新和任务重置...")
        tableService.resetAllFlags()
        // 重载系统配置
        configService.loadConfig()
    }

    val etlQueueStatus: Map<String, Any>
        /**
         * 获取采集任务队列的详细状态
         */
        get() {
            val detailedStatus = queueManager.getQueueStatus()

            try {
                // 添加数据库中待处理任务数量
                val pending = tableService.findPendingTasks()
                if (pending > 0) detailedStatus["pendingInDatabase"] = pending
                val running = tableService.findRunningTasks()
                if (running > 0) detailedStatus["runningInDatabase"] = running
            } catch (e: Exception) {
                log.error("获取数据库任务状态失败", e)
            }

            return detailedStatus
        }

    /**
     * 停止队列监控
     */
    fun stopQueueMonitor(): String {
        queueManager.stopQueueMonitor()
        return "队列监控停止信号已发送"
    }

    /**
     * 启动队列监控
     */
    fun startQueueMonitor(): String {
        queueManager.startQueueMonitor()
        return "队列监控启动信号已发送"
    }

    /**
     * 清空队列并重新扫描
     */
    fun resetQueue(): String = queueManager.resetQueue()

    // 特殊任务提醒
    fun findAllSpecialTask(): List<EtlTable> = tableService.findAllSpecialTask()

    // 提交采集任务到队列
    fun submitTask(taskId: Long): TaskResultDto = queueManager.submitTask(taskId)

    fun allTaskStatus(): List<Map<String, Any>?>? {
        return queueManager.getAllTaskStatus()
    }
}
