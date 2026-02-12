package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.model.VwEtlTableWithSource;

/**
 * 目标端能力适配器。
 * 负责目标端建表/更新、运行前准备（如分区）和增量位点查询等能力。
 */
public interface TargetAdapter
{
    /**
     * 目标端类型标识（如 HIVE）。
     */
    String getType();

    boolean addPartition(long taskId, String db, String table, String partName, String partValue);

    boolean createOrUpdateTable(VwEtlTableWithSource etlTable);

    Long getMaxValue(VwEtlTableWithSource table, String columnName, String partValue);
}
