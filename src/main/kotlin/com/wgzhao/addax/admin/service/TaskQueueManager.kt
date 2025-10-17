package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.success
import com.wgzhao.addax.admin.model.EtlTable
import com.wgzhao.addax.admin.utils.CommandExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.Volatile
import kotlin.math.max

@Component
class TaskQueueManager(
    private val dictService: DictService,
    private val addaxLogService: AddaxLogService,
    private val alertService: AlertService,
    private val tableService: TableService,
    private val jourService: EtlJourService,
    private val jobContentService: JobContentService,
    private val targetService: TargetService
) {
    private val log = KotlinLogging.logger {}
    private val queueSize = 100
    private val concurrentLimit = 30

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val etlQueue = Channel<EtlTable>(queueSize)
    private val semaphore = Semaphore(concurrentLimit)
    @Volatile
    private var queueMonitorRunning = false

    @PostConstruct
    fun init() {
        startQueueMonitor()
    }

    fun submitTask(taskId: Long): TaskResultDto {
        val etlTable = tableService.getTable(taskId) ?: return failure("task not found", 0)
        return if (etlQueue.trySend(etlTable).isSuccess) success("任务已加入队列", 0) else failure("队列已满", 0)
    }

    suspend fun scanAndEnqueueEtlTasks() {
        try {
            val tasks = tableService.getRunnableTasks()
            if (tasks.isNullOrEmpty()) return
            log.info { "扫描到 ${tasks.size} 个待采集任务" }
            var enqueuedCount = 0
            var skippedCount = 0
            for (task in tasks.filterNotNull()) {
                if (etlQueue.trySend(task).isSuccess) {
                    enqueuedCount++
                } else {
                    skippedCount++
                }
            }
            log.info {
                "任务入队完成: 成功入队 ${enqueuedCount} 个，跳过 ${skippedCount} 个，当前队列大小: ${queueSize}" }
        } catch (e: Exception) {
            log.error(e) { "扫描和入队采集任务失败" }
            alertService.sendToWeComRobot("扫描采集任务失败: " + e.message)
        }
    }

    fun startQueueMonitor() {
        if (!queueMonitorRunning) {
            queueMonitorRunning = true
            scope.launch { queueMonitorLoop() }
            log.info { "采集任务队列监控器已启动，队列容量: ${queueSize}, 并发限制: ${concurrentLimit}" }
        }
    }

    fun getQueueStatus(): MutableMap<String, Any> = mutableMapOf(
        "queueSize" to queueSize,
        "queueCapacity" to queueSize,
        "concurrentLimit" to concurrentLimit,
        "queueMonitorRunning" to queueMonitorRunning,
        "timestamp" to LocalDateTime.now()
    )

    suspend fun resetQueue(): String {
        val cleared = clearQueue()
        scanAndEnqueueEtlTasks()
        return "已清空队列（$cleared 项）并重新扫描"
    }

    private suspend fun queueMonitorLoop() {
        log.info { "队列监控器开始运行，并发限制: $concurrentLimit" }
        for (task in etlQueue) {
            semaphore.withPermit {
                scope.launch {
                    executeEtlTaskWithConcurrencyControl(task)
                }
            }
        }
        log.info { "队列监控器已停止" }
    }

    fun executeEtlTaskWithConcurrencyControl(task: EtlTable): TaskResultDto {
        val tid: Long = task.id ?: -1L
        val startTime = System.currentTimeMillis()
        try {
            tableService.setRunning(task)
            val result = executeEtlTaskLogic(task)
            val duration = max((System.currentTimeMillis() - startTime) / 1000, 0)
            log.info { "采集任务 ${tid} 执行完成，耗时: ${duration}s, 结果: ${result}" }
            task.duration = duration
            if (result) {
                tableService.setFinished(task)
                return success("执行成功", duration)
            } else {
                tableService.setFailed(task)
                alertService.sendToWeComRobot(
                    String.format(
                        "采集任务执行失败: %s",
                        tid
                    )
                )
                return failure("执行失败：Addax 退出非0", duration)
            }
        } catch (e: Exception) {
            val duration = (System.currentTimeMillis() - startTime) / 1000
            log.error(e) { "采集任务 $tid 执行失败，耗时: ${duration}s" }
            task.duration = duration
            tableService.setFailed(task)
            val msg = e.message ?: "内部异常"
            alertService.sendToWeComRobot(String.format("采集任务执行失败: %s, 错误: %s", tid, msg))
            return failure("执行异常: $msg", duration)
        }
    }

    fun executeEtlTaskLogic(task: EtlTable): Boolean {
        val taskId: Long = task.id ?: return false
        log.info { "执行采集任务逻辑: taskId=${taskId}, destDB=${task.targetDb}, tableName=${task.targetTable}" }
        var job = jobContentService.getJobContent(taskId) ?: return false
        val logDate = dictService.getBizDate()
        val dwCltDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val partFormat: String = task.partFormat ?: ""
        val bizDate = if (partFormat.isNotBlank() && partFormat != "yyyyMMdd") LocalDate.parse(
            logDate,
            DateTimeFormatter.ofPattern("yyyyMMdd")
        ).format(DateTimeFormatter.ofPattern(partFormat)) else logDate
        log.info { "biz date is ${bizDate}, dw_clt_date is ${dwCltDate}, dw_trade_date is ${logDate}" }
        job = job.replace("\${'$'}{logdate}", bizDate).replace("\${'$'}{dw_clt_date}", dwCltDate)
            .replace("\${'$'}{dw_trade_date}", logDate)
        val partName = task.partName
        if (!partName.isNullOrEmpty()) {
            val targetDb = task.targetDb ?: return false
            val targetTable = task.targetTable ?: return false
            if (!targetService.addPartition(taskId, targetDb, targetTable, partName, bizDate)) return false
        }
        val tempFile: File = try {
            File.createTempFile("${task.targetDb}.${task.targetTable}_", ".json")
                .also { it.writeText(job) } // Kotlin 扩展函数
        } catch (e: IOException) {
            log.error(e) { "写入临时文件失败" }
            return false
        }
        log.debug { "采集任务 $taskId 的Job已写入临时文件: ${tempFile.absolutePath}" }
        val logName = String.format("addax_%s_%d.log", taskId, System.currentTimeMillis())
        val cmd = String.format(
            "%s/bin/addax.sh  -p'-DjobName=%d -Dlog.file.name=%s' %s",
            dictService.getAddaxHome(),
            taskId,
            logName,
            tempFile.absolutePath
        )
        val retCode = executeAddax(cmd, taskId, logName)
        log.info {"采集任务 $taskId 的日志已写入文件: $logName" }
        return retCode
    }

    fun stopQueueMonitor() {
        queueMonitorRunning = false
        scope.cancel()
        etlQueue.close()
        log.info { "队列监控器停止信号已发送" }
    }

    fun getAllTaskStatus(): Map<String, Any> = getQueueStatus()

    fun clearQueue(): Int {
        val size = queueSize
        etlQueue.close()
        log.info { "已清空队列，清除了 $size 个任务" }
        return size
    }

    @PreDestroy
    fun shutdown() {
        log.info { "开始关闭采集任务队列管理器..." }
        stopQueueMonitor()
        log.info { "采集任务队列管理器已关闭" }
    }

    private fun executeAddax(command: String?, tid: Long, logName: String?): Boolean {
        log.info { "Executing command: $command" }
        val etlJour = jourService.addJour(tid, JourKind.COLLECT, command)
        val taskResult = CommandExecutor.executeWithResult(command)
        val path = File(dictService.getAddaxHome() + "/log/" + logName)
        try {
            addaxLogService.insertLog(tid, path.readText()) // Kotlin 扩展函数
        } catch (e: IOException) {
            log.warn(e) { "Failed to get the addax log content from  ${path}" }
        }
        etlJour.duration = taskResult.durationSeconds
        etlJour.status = taskResult.success
        if (!taskResult.success) {
            log.error { "Addax 采集任务 $tid 执行失败，退出码: ${taskResult.message}" }
            etlJour.status = false
            etlJour.errorMsg = taskResult.message
        }
        jourService.saveJour(etlJour)
        return taskResult.success
    }
}
