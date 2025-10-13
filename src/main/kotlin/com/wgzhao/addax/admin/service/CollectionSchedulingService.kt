package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.event.SourceUpdatedEvent
import com.wgzhao.addax.admin.model.EtlSource
import com.wgzhao.addax.admin.repository.EtlSourceRepo
import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.util.function.Consumer

@Service
@Slf4j
class CollectionSchedulingService {
    @Autowired
    private val taskSchedulerService: TaskSchedulerService? = null

    @Autowired
    private val etlSourceRepo: EtlSourceRepo? = null

    @Autowired
    private val taskService: TaskService? = null

    @Autowired
    private val systemConfigService: SystemConfigService? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        rescheduleAllTasks()
    }

    fun rescheduleAllTasks() {
        try {
            // Schedule collection tasks for each source
            val sources: MutableList<EtlSource>? = etlSourceRepo!!.findByEnabled(true)
            for (source in sources!!) {
                scheduleOrUpdateTask(source)
            }
            // Schedule the daily parameter update task
            scheduleDailyParamUpdate()
        } catch (e: Exception) {
            // 记录详细异常，便于排查
            CollectionSchedulingService.log.error("Error in rescheduleAllTasks: ", e)
        }
    }

    private fun scheduleDailyParamUpdate() {
        val time = systemConfigService!!.getSwitchTimeAsTime()
        val cronExpression = convertLocalTimeToCron(time)
        val task = Runnable { taskService!!.updateParams() }
        taskSchedulerService!!.scheduleTask("dailyParamUpdate", task, cronExpression)
    }

    fun scheduleOrUpdateTask(source: EtlSource) {
        val taskId = "source-" + source.getCode()
        if (source.isEnabled() && source.getStartAt() != null) {
            CollectionSchedulingService.log.info("Scheduling task for source {} at {}", source.getCode(), source.getStartAt())
            val cronExpression = convertLocalTimeToCron(source.getStartAt())
            // cancel existing task if any
            taskSchedulerService!!.cancelTask(taskId)
            val task = Runnable { taskService!!.executeTasksForSource(source.getId()) }
            taskSchedulerService.scheduleTask(taskId, task, cronExpression)
        } else {
            taskSchedulerService!!.cancelTask(taskId)
        }
    }

    fun cancelTask(code: String) {
        val taskId = "source-" + code
        taskSchedulerService!!.cancelTask(taskId)
    }

    private fun convertLocalTimeToCron(time: LocalTime): String {
        return String.format("0 %d %d * * ?", time.getMinute(), time.getHour())
    }

    @EventListener
    fun handleSourceEvent(event: SourceUpdatedEvent) {
        if (event.isScheduleChanged()) {
            etlSourceRepo!!.findById(event.getSourceId()).ifPresent(Consumer { source: EtlSource? -> this.scheduleOrUpdateTask(source!!) })
        }
    }
}
