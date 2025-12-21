package com.wgzhao.addax.admin.service;

import lombok.AllArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@AllArgsConstructor
public class TaskSchedulerService
{

    private final TaskScheduler taskScheduler;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public void scheduleTask(String taskId, Runnable task, String cronExpression)
    {
        // Cancel any existing task with the same ID
        cancelTask(taskId);
        // Schedule the new task
        ScheduledFuture<?> future = taskScheduler.schedule(task, new CronTrigger(cronExpression));
        scheduledTasks.put(taskId, future);
    }

    public void cancelTask(String taskId)
    {
        ScheduledFuture<?> existingTask = scheduledTasks.get(taskId);
        if (existingTask != null) {
            existingTask.cancel(true);
            scheduledTasks.remove(taskId);
        }
    }

    public void cancelAll()
    {
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null) {
                future.cancel(true);
            }
        }
        scheduledTasks.clear();
    }
}
