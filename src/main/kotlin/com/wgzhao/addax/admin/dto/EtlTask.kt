package com.wgzhao.addax.admin.dto

/**
 * 采集任务数据结构
 */
class EtlTask(// getters
    val taskId: String?, val taskType: String?, val taskData: MutableMap<String?, Any?>?
) {
    val createTime: Long

    init {
        this.createTime = System.currentTimeMillis()
    }

    override fun toString(): String {
        return String.format(
            "EtlTask{taskId='%s', taskType='%s', createTime=%d}",
            taskId, taskType, createTime
        )
    }
}
