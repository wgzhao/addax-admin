package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.success
import com.wgzhao.addax.admin.model.EtlStatistic
import com.wgzhao.addax.admin.model.EtlTable
import com.wgzhao.addax.admin.utils.CommandExecutor
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile
import kotlin.math.max

/**
 * 采集任务队列管理器
 * 负责管理固定长度（100）的采集任务队列，控制30个并发采集程序
 */
@Component
@Slf4j
class TaskQueueManager {
    private val queueSize = 100

    private val concurrentLimit = 30

    @Autowired
    private val dictService: DictService? = null

    @Autowired
    private val addaxLogService: AddaxLogService? = null

    @Autowired
    private val statService: StatService? = null

    @Autowired
    private val alertService: AlertService? = null

    @Autowired
    private val tableService: TableService? = null

    @Autowired
    private val jourService: EtlJourService? = null

    @Autowired
    private val configService: SystemConfigService? = null

    @Autowired
    private val jobContentService: JobContentService? = null

    @Autowired
    private val targetService: TargetService? = null

    // 队列监控标志
    @Volatile
    private var queueMonitorRunning = false

    // 采集任务队列 - 固定长度100
    val etlQueue: BlockingQueue<EtlTable?> = ArrayBlockingQueue<EtlTable?>(100)

    // 当前执行中的采集任务数
    private val runningTaskCount = AtomicInteger(0)

    // 采集任务监控线程池
    private val queueMonitorExecutor: ExecutorService = Executors.newSingleThreadExecutor(ThreadFactory { r: Runnable? ->
        val t = Thread(r, "etl-queue-monitor")
        t.setDaemon(true)
        t
    })

    // 采集任务执行线程池
    private val etlExecutor: ExecutorService = Executors.newCachedThreadPool(ThreadFactory { r: Runnable? ->
        val t = Thread(r, "etl-worker")
        t.setDaemon(true)
        t
    })

    @PostConstruct
    fun init() {
        startQueueMonitor()
    }

    /**
     * 扫描并将采集任务加入队列
     * 其逻辑为:
     * 1. 先检查当前时间是否小于切日时间(SWITCH_TIME),如果小于，则 扫描tb_imp_etl表中flag字段为N的记录
     * 2. 否则，需要检查任务设定的采集时间是否小于当前时间
     * 比如，假定是T 日下午 16：:30切日，且采集时间设定为 02:50，则表示需要在 T+1 日后的 02:50 之后才能采集
     * 当如果采集时间设定的为 14:30，则需要 T+1 日后的 14:30 之后才能采集
     *
     */
    fun scanAndEnqueueEtlTasks() {
        try {
            val tasks = tableService!!.getRunnableTasks()
            if (tasks.isEmpty()) {
                return
            }
            TaskQueueManager.log.info("扫描到 {} 个待采集任务", tasks.size)

            var enqueuedCount = 0
            var skippedCount = 0

            for (task in tasks) {
                // 尝试将任务加入队列（非阻塞）

                if (etlQueue.offer(task)) {
                    enqueuedCount++
                    TaskQueueManager.log.debug("任务 {} 已加入队列", task.getId())
                } else {
                    skippedCount++
                    TaskQueueManager.log.warn("队列已满，任务 {} 未能加入队列", task.getId())
                }
            }

            TaskQueueManager.log.info(
                "任务入队完成: 成功入队 {} 个，跳过 {} 个，当前队列大小: {}",
                enqueuedCount, skippedCount, etlQueue.size
            )
        } catch (e: Exception) {
            TaskQueueManager.log.error("扫描和入队采集任务失败", e)
            alertService!!.sendToWeComRobot("扫描采集任务失败: " + e.message)
        }
    }

    /**
     * 启动队列监控器
     */
    fun startQueueMonitor() {
        if (!queueMonitorRunning) {
            queueMonitorRunning = true
            queueMonitorExecutor.submit(Runnable { this.queueMonitorLoop() })
            TaskQueueManager.log.info("采集任务队列监控器已启动，队列容量: {}, 并发限制: {}", queueSize, concurrentLimit)
        }
    }

    /**
     * 队列监控循环 - 从队列获取任务并控制并发执行
     */
    private fun queueMonitorLoop() {
        TaskQueueManager.log.info("队列监控器开始运行，并发限制: {}", concurrentLimit)

        while (queueMonitorRunning) {
            try {
                // 检查当前并发数是否达到限制
                if (runningTaskCount.get() >= concurrentLimit) {
                    // 并发已满，等待一段时间后重试
                    Thread.sleep(1000)
                    continue
                }

                // 从队列中获取任务（阻塞等待，最多等待5秒）
                val task = etlQueue.poll(5, TimeUnit.SECONDS)
                if (task == null) {
                    // 队列为空，继续监控
                    continue
                }

                // 增加运行任务计数
                val currentRunning = runningTaskCount.incrementAndGet()
                TaskQueueManager.log.info(
                    "从队列获取任务: {}, 当前并发数: {}/{}",
                    task.getId(), currentRunning, concurrentLimit
                )

                // 提交任务到执行线程池
                etlExecutor.submit<TaskResultDto?>(Callable { executeEtlTaskWithConcurrencyControl(task) })
            } catch (e: InterruptedException) {
                TaskQueueManager.log.info("队列监控器被中断")
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                TaskQueueManager.log.error("队列监控器异常", e)
                // 发生异常时等待一段时间再继续
                try {
                    Thread.sleep(5000)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        TaskQueueManager.log.info("队列监控器已停止")
    }

    /**
     * 执行采集任务并控制并发
     * 返回详细执行结果，包含成功/失败、错误码、消息、耗时等
     */
    fun executeEtlTaskWithConcurrencyControl(task: EtlTable): TaskResultDto {
        val tid: Long = task.getId()
        val startTime = System.currentTimeMillis()

        try {
            // 更新任务状态为运行中
            tableService!!.setRunning(task)
            // 执行具体的采集逻辑（这里先调用现有的采集方法框架）
            val result = executeEtlTaskLogic(task)

            val duration = max((System.currentTimeMillis() - startTime) / 1000, 0) // seconds
            TaskQueueManager.log.info("采集任务 {} 执行完成，耗时: {}s, 结果: {}", tid, duration, result)
            task.setDuration(duration)
            // 更新任务状态为成功
            if (result) {
                tableService.setFinished(task)
                return success("执行成功", duration)
            } else {
                tableService.setFailed(task)
                alertService!!.sendToWeComRobot(String.format("采集任务执行失败: %s", tid))
                return failure("执行失败：Addax 退出非0", duration)
            }
        } catch (e: Exception) {
            val duration = (System.currentTimeMillis() - startTime) / 1000 // seconds
            TaskQueueManager.log.error("采集任务 {} 执行失败，耗时: {}s", tid, duration, e)
            // 更新任务状态为失败
            task.setDuration(duration)
            tableService!!.setFailed(task)

            // 发送告警
            alertService!!.sendToWeComRobot(String.format("采集任务执行失败: %s, 错误: %s", tid, e.message))
            val msg = if (e.message == null) "内部异常" else e.message
            return failure("执行异常: " + msg, duration)
        } finally {
            // 减少运行任务计数
            val currentRunning = runningTaskCount.decrementAndGet()
            TaskQueueManager.log.debug("任务 {} 执行结束，当前并发数: {}", tid, currentRunning)
        }
    }

    /**
     * 执行具体的采集逻辑
     */
    fun executeEtlTaskLogic(task: EtlTable): Boolean {
        val taskId: Long = task.getId()

        TaskQueueManager.log.info(
            "执行采集任务逻辑: taskId={}, destDB={}, tableName={}",
            taskId, task.getTargetDb(), task.getTargetTable()
        )
        // 生成已提交任务流水
        var job = jobContentService!!.getJobContent(taskId)
        if (job == null || job.isEmpty()) {
            TaskQueueManager.log.warn("模板未生成, taskId = {}", taskId)
            return false
        }

        val logDate = configService!!.getBizDate() //yyyyMMdd
        val dw_clt_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        // 分区字段的日期格式需要根据采集表中的 part_format 来进行格式化
        val partFormat: String = task.getPartFormat()
        var bizDate = logDate
        if (!partFormat.isBlank() && partFormat != "yyyyMMdd") {
            bizDate = LocalDate.parse(logDate, DateTimeFormatter.ofPattern("yyyyMMdd"))
                .format(DateTimeFormatter.ofPattern(partFormat))
        }
        TaskQueueManager.log.info("biz date is {}, dw_clt_date is {}, dw_trade_date is {}", bizDate, dw_clt_date, logDate)
        job = job.replace("\${logdate}", bizDate).replace("\${dw_clt_date}", dw_clt_date).replace("\${dw_trade_date}", logDate)
        if (task.getPartName() != "") {
            // hive 创建分区, 尝试用 hive 命令行创建分区
            val result = targetService!!.addPartition(taskId, task.getTargetDb(), task.getTargetTable(), task.getPartName(), bizDate)
            if (!result) {
                return false
            }
        }
        // 写入临时文件
        val tempFile: File?
        try {
            tempFile = File.createTempFile(task.getTargetDb() + "." + task.getTargetTable() + "_", ".json")
            Files.writeString(tempFile.toPath(), job)
        } catch (e: IOException) {
            TaskQueueManager.log.error("写入临时文件失败", e)
            return false
        }

        TaskQueueManager.log.debug("采集任务 {} 的Job已写入临时文件: {}", taskId, tempFile.getAbsolutePath())
        // 设定一个日志文件名的名称
        val logName = String.format("addax_%s_%d.log", taskId, System.currentTimeMillis())
        val cmd = String.format("%s/bin/addax.sh  -p'-DjobName=%d -Dlog.file.name=%s' %s", dictService!!.getAddaxHome(), taskId, logName, tempFile.getAbsolutePath())
        val retCode = executeAddax(cmd, taskId, logName)
        TaskQueueManager.log.info("采集任务 {} 的日志已写入文件: {}", taskId, logName)
        return retCode
    }

    /**
     * 手动添加任务到队列
     */
    fun addTaskToQueue(tid: Long): Boolean {
        return addTaskToQueue(tableService!!.getTable(tid))
    }

    fun addTaskToQueue(task: EtlTable): Boolean {
        val added = etlQueue.offer(task)
        if (added) {
            TaskQueueManager.log.info("手动添加任务到队列: {}", task.getId())
        } else {
            TaskQueueManager.log.warn("队列已满，无法添加任务: {}", task.getId())
        }
        return added
    }

    /**
     * 停止队列监控器
     */
    fun stopQueueMonitor() {
        queueMonitorRunning = false
        TaskQueueManager.log.info("队列监控器停止信号已发送")
    }

    val queueStatus: MutableMap<String?, Any?>
        /**
         * 获取队列状态信息
         */
        get() {
            val status: MutableMap<String?, Any?> = HashMap<String?, Any?>()
            status.put("queueSize", etlQueue.size)
            status.put("queueCapacity", queueSize)
            status.put("runningTaskCount", runningTaskCount.get())
            status.put("concurrentLimit", concurrentLimit)
            status.put("queueMonitorRunning", queueMonitorRunning)
            status.put("timestamp", LocalDateTime.now())
            return status
        }

    /**
     * 清空队列
     */
    fun clearQueue(): Int {
        val size = etlQueue.size
        etlQueue.clear()
        TaskQueueManager.log.info("已清空队列，清除了 {} 个任务", size)
        return size
    }

    /**
     * 应用关闭时的清理工作
     */
    @PreDestroy
    fun shutdown() {
        TaskQueueManager.log.info("开始关闭采集任务队列管理器...")

        // 停止队列监控
        stopQueueMonitor()

        // 关闭线程池
        queueMonitorExecutor.shutdown()
        etlExecutor.shutdown()

        try {
            // 等待线程池关闭
            if (!queueMonitorExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                queueMonitorExecutor.shutdownNow()
            }
            if (!etlExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                etlExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            queueMonitorExecutor.shutdownNow()
            etlExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        TaskQueueManager.log.info("采集任务队列管理器已关闭")
    }

    /**
     * 执行 Addax 采集命令，并处理日志输出
     *
     * @param command Addax 执行命令
     * @param tid 采集表主键
     * @param logName addax 输出的日志文件名
     * @return 程序退出码
     */
    private fun executeAddax(command: String?, tid: Long, logName: String?): Boolean {
        TaskQueueManager.log.info("Executing command: {}", command)
        val etlJour = jourService!!.addJour(tid, JourKind.COLLECT, command)

        val taskResult = CommandExecutor.executeWithResult(command)
        // 记录日志
        val path = Path.of(dictService!!.getAddaxHome() + "/log/" + logName)
        try {
            val logContent = Files.readString(path)
            addaxLogService!!.insertLog(tid, logContent)
        } catch (e: IOException) {
            TaskQueueManager.log.warn("Failed to get the addax log content: {}", path)
        }
        etlJour.setDuration(taskResult.getDurationSeconds())
        etlJour.setStatus(true)
        if (!taskResult.isSuccess()) {
            TaskQueueManager.log.error("Addax 采集任务 {} 执行失败，退出码: {}", tid, taskResult.getMessage())
            etlJour.setStatus(false)
            etlJour.setErrorMsg(taskResult.getMessage())
        }
        jourService.saveJour(etlJour)
        return taskResult.isSuccess()
    }

    /**
     * 分析 Addax 采集的最后 8 行信息，提取统计信息，并插入到 tb_addax_sta 表中
     * 最后 8 行的信息类似如下：
     *
     *
     * Job start  at             : 2025-09-16 09:00:50
     * Job end    at             : 2025-09-16 09:01:00
     * Job took secs             :                  9s
     * Total   bytes             :               41841
     * Average   bps             :            6.81KB/s
     * Average   rps             :             74rec/s
     * Number of rec             :                 449
     * Failed record             :                   0
     *
     * @param tid 采集表主键
     * @param lastLines 最后的统计信息
     */
    private fun processAddaxStatistics(tid: Long, lastLines: LinkedList<String>) {
        val stats: MutableMap<String?, String?> = HashMap<String?, String?>()
        for (line in lastLines) {
            val parts: Array<String?> = line.split(":".toRegex(), limit = 2).toTypedArray()
            if (parts.size == 2) {
                stats.put(parts[0]!!.trim { it <= ' ' }, parts[1]!!.trim { it <= ' ' })
            }
        }
        if (stats.size < 8) {
            TaskQueueManager.log.warn("无法解析 Addax 统计信息，行数不足: {}", stats)
            return
        }
        if (!stats.containsKey("Job start  at") || stats.get("Job start  at")!!.isEmpty()) {
            TaskQueueManager.log.error("无法解析 Addax 统计信息，缺少 Job start  at 字段: {}", stats)
            return
        }
        val jobStart = stats.get("Job start  at")!!.replace(" ", "T")
        val jobEnd = stats.get("Job end    at")!!.replace(" ", "T")

        val statistic = EtlStatistic()
        statistic.setTid(tid)
        statistic.setRunDate(LocalDate.now())
        statistic.setStartAt(LocalDateTime.parse(jobStart))
        statistic.setEndAt(LocalDateTime.parse(jobEnd))
        val jobTook = stats.get("Job took secs")!!.replace("s", "").toLong()
        statistic.setTakeSecs(jobTook)
        val totalBytes = stats.get("Total   bytes")!!.toLong()
        statistic.setTotalBytes(totalBytes)
        val numberOfRec = stats.get("Number of rec")!!.toLong()
        statistic.setTotalRecs(numberOfRec)
        val failedRecord = stats.get("Failed record")!!.toLong()
        statistic.setTotalErrors(failedRecord)
        val averageBps = totalBytes / (if (jobTook == 0L) 1 else jobTook)
        statistic.setByteSpeed(averageBps)
        val averageRps = numberOfRec / (if (jobTook == 0L) 1 else jobTook)
        statistic.setRecSpeed(averageRps)

        if (statService!!.saveOrUpdate(statistic)) {
            TaskQueueManager.log.info("Addax 采集统计信息已插入 tb_addax_statistic 表: {}", statistic)
        }
    }
}
