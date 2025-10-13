package com.wgzhao.addax.admin.common

/**
 * 采集任务状态
 *
 * @author wgzhao
 */
object TaskStatus {
    // 采集任务状态
    const val COLLECTING: String = "R" // 采集中
    const val COLLECTED: String = "Y" // 采集完成
    const val COLLECT_FAIL: String = "F" // 采集失败
    const val WAITING_COLLECT: String = "W" // 等待采集
    const val EXCLUDE_COLLECT: String = "X" // 不采集
}
