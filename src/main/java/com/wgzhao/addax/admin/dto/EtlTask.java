package com.wgzhao.addax.admin.dto;

import java.util.Map;

/**
 * 采集任务数据结构
 */
public class EtlTask {
    private final String taskId;
    private final String taskType;
    private final Map<String, Object> taskData;
    private final long createTime;

    public EtlTask(String taskId, String taskType, Map<String, Object> taskData) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.taskData = taskData;
        this.createTime = System.currentTimeMillis();
    }

    // getters
    public String getTaskId() { return taskId; }
    public String getTaskType() { return taskType; }
    public Map<String, Object> getTaskData() { return taskData; }
    public long getCreateTime() { return createTime; }

    @Override
    public String toString() {
        return String.format("EtlTask{taskId='%s', taskType='%s', createTime=%d}",
                           taskId, taskType, createTime);
    }
}
