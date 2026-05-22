package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlTable;

import java.util.Map;

public interface TaskQueueManager
{
    void scanAndEnqueueEtlTasks();

    void startQueueMonitor();

    void stopQueueMonitor();

    void restartQueueMonitor();

    boolean addTaskToQueue(EtlTable etlTable);

    boolean addTaskToQueue(long tableId);

    default boolean addTaskToQueue(EtlTable etlTable, String payload)
    {
        return addTaskToQueue(etlTable);
    }

    default boolean addTaskToQueue(long tableId, String payload)
    {
        return addTaskToQueue(tableId);
    }

    /**
     * Returns true while schema refresh is in progress (queue monitor stopped).
     * Use this instead of checking the Redis schema refresh lock key, which is no longer written.
     */
    default boolean isRefreshing()
    {
        return false;
    }

    int clearQueue();

    Map<String, Object> getQueueStatus();

    TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable etlTable);

    void truncateQueueExceptRunningTasks();
}
