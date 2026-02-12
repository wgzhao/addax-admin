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

    /**
     * 执行任务前的目标端准备动作（如分区创建）。
     */
    boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, String bizDateValue);

    /**
     * 生成目标端 writer 模板片段。
     */
    String buildWriterJob(VwEtlTableWithSource table);
}
