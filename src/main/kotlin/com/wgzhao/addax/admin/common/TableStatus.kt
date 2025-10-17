package com.wgzhao.addax.admin.common

object TableStatus {
    // 表采集状态
    // 未采集
    const val NOT_COLLECT: String = "N"

    // 采集中
    const val COLLECTING: String = "R"

    //采集完成
    const val COLLECTED: String = "Y"

    //采集失败
    const val COLLECT_FAIL: String = "E"

    //等待采集
    const val WAITING_COLLECT: String = "W"

    //不采集
    const val EXCLUDE_COLLECT: String = "X"

    //等待同步表结构
    const val WAIT_SCHEMA: String = "U"
}
