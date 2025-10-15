package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.model.EtlTable
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val log = KotlinLogging.logger {}

    /**
     * 执行指定采集源下的所有采集任务，将任务加入队列
     * @param sourceId 采集源ID
     */
    fun executeTasksForSource(sourceId: Int) {
        val tables = tableService.getRunnableTasksBySid(sourceId)
        tables.filterNotNull().forEach { queueManager.etlQueue.offer(it) }
        log.info { "Executing tasks for source ${sourceId}, found ${tables.size} tables" }
    }

    /**
     * 计划任务主控 - 基于队列的采集任务管理
     * 入口方法，负责扫描tb_imp_etl表并管理采集队列
     */
    fun executePlanStartWithQueue() {
        // 启动队列监控器（如果还未启动）
        queueManager.startQueueMonitor()

        // 扫描tb_imp_etl表中flag字段为N的记录并加入队列
        queueManager.scanAndEnqueueEtlTasks()
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    fun updateParams() {
        // 在切日时间，开始重置所有采集任务的 flag 字段设置为 'N'，以便重新采集
        log.info { "开始执行每日参数更新和任务重置..." }
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
                log.error(e) { "获取数据库任务状态失败" }
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
    fun findAllSpecialTask(): List<EtlTable?>? = tableService.findSpecialTasks()

    // 提交采集任务到队列
    fun submitTask(taskId: Long): TaskResultDto = queueManager.submitTask(taskId)

    fun getAllTaskStatus(): List<Map<String, Any>?>? {
        val sql = """
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
                """.trimIndent()
        return jdbcTemplate.queryForList(sql) as List<Map<String, Any>?>?
    }
}
