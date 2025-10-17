package com.wgzhao.addax.admin.common

object JourKind {
    // 流水类型
    const val COLLECT: String = "COLLECT" // 表采集
    const val ADDAX_JOB: String = "ADDAX_JOB" // 生成采集模板
    const val SCHEMA: String = "SCHEMA" // 表结构同步
    const val PARTITION: String = "PARTITION" // 创建表分区
    const val UPDATE_TABLE: String = "UPDATE_TABLE" // 创建目标表
    const val UPDATE_COLUMN: String = "UPDATE_COLUMN" // 表字段更新
    const val CREATE_COLUMN: String = "CREATE_COLUMN" // 首次同步表结构
}
