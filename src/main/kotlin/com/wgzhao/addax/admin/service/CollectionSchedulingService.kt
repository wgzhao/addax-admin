package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.event.SourceUpdatedEvent
import com.wgzhao.addax.admin.model.EtlSource
import com.wgzhao.addax.admin.repository.EtlSourceRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.util.function.Consumer

@Service
class CollectionSchedulingService(
    private val taskSchedulerService: TaskSchedulerService,
    private val etlSourceRepo: EtlSourceRepo,
    private val taskService: TaskService,
    private val systemConfigService: SystemConfigService
    ) {

    private val log = KotlinLogging.logger {}

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        rescheduleAllTasks()
    }

    fun rescheduleAllTasks() {
        try {
            // Schedule collection tasks for each source
            val sources: MutableList<EtlSource>? = etlSourceRepo.findByEnabled(true)
            for (source in sources!!) {
                scheduleOrUpdateTask(source)
            }
            // Schedule the daily parameter update task
            scheduleDailyParamUpdate()
        } catch (e: Exception) {
            // 记录详细异常，便于排查
            log.error(e) { "Error in rescheduleAllTasks" }
        }
    }

    private fun scheduleDailyParamUpdate() {
        val time = systemConfigService.switchTimeAsTime
        val cronExpression = convertLocalTimeToCron(time)
        val task = Runnable { taskService.updateParams() }
        taskSchedulerService.scheduleTask("dailyParamUpdate", task, cronExpression)
    }

    fun scheduleOrUpdateTask(source: EtlSource) {
        val taskId = "source-${source.code}"
        if (source.enabled && source.startAt != null) {
            log.info {"Scheduling task for source ${source.code} at ${source.startAt}" }
            val cronExpression = convertLocalTimeToCron(source.startAt!!)
            // cancel existing task if any
            taskSchedulerService.cancelTask(taskId)
            val task = Runnable { taskService.executeTasksForSource(source.id) }
            taskSchedulerService.scheduleTask(taskId, task, cronExpression)
        } else {
            taskSchedulerService.cancelTask(taskId)
        }
    }

    fun cancelTask(code: String) {
        taskSchedulerService.cancelTask("source-${code}")
    }

    private fun convertLocalTimeToCron(time: LocalTime): String {
        return String.format("0 %d %d * * ?", time.minute, time.hour)
    }

    @EventListener
    fun handleSourceEvent(event: SourceUpdatedEvent) {
        if (event.connectionChanged) {
            etlSourceRepo.findById(event.sourceId).ifPresent(Consumer { source: EtlSource? -> this.scheduleOrUpdateTask(source!!) })
        }
    }
}
