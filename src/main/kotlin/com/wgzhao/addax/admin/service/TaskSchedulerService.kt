package com.wgzhao.addax.admin.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Service
class TaskSchedulerService {
    @Autowired
    private val taskScheduler: TaskScheduler? = null

    private val scheduledTasks: MutableMap<String?, ScheduledFuture<*>?> = ConcurrentHashMap<String?, ScheduledFuture<*>?>()

    fun scheduleTask(taskId: String?, task: Runnable, cronExpression: String) {
        // Cancel any existing task with the same ID
        cancelTask(taskId)
        // Schedule the new task
        val future = taskScheduler!!.schedule(task, CronTrigger(cronExpression))
        scheduledTasks.put(taskId, future)
    }

    fun cancelTask(taskId: String?) {
        val existingTask = scheduledTasks.get(taskId)
        if (existingTask != null) {
            existingTask.cancel(true)
            scheduledTasks.remove(taskId)
        }
    }
}

