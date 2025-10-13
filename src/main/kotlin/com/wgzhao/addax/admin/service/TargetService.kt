package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import org.springframework.stereotype.Service

/**
 * 采集任务目标管理服务。
 * 主要负责 Hadoop 目标表的创建、分区管理、元数据更新等操作。
 * 包括 Hive 表的创建/更新、分区添加等功能。
 */
@Service
interface TargetService {
    /**
     * 为指定 Hive 表添加分区。
     *
     * @param taskId 采集任务ID
     * @param db Hive数据库名
     * @param table Hive表名
     * @param partName 分区字段名
     * @param partValue 分区字段值
     * @return 是否添加成功
     */
    fun addPartition(taskId: Long, db: String?, table: String?, partName: String?, partValue: String?): Boolean

    /**
     * 创建或更新 Hive 目标表。
     * 包括建库、建表、分区、表属性等操作。
     *
     * @param etlTable 采集表视图对象
     * @return 是否创建/更新成功
     */
    fun createOrUpdateHiveTable(etlTable: VwEtlTableWithSource?): Boolean
}
