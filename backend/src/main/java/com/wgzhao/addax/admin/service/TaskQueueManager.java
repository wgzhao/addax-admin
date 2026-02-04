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

    int clearQueue();

    Map<String, Object> getQueueStatus();

    TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable etlTable);

    void truncateQueueExceptRunningTasks();
}
