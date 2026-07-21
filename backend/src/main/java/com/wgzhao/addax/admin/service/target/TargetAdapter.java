package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.model.VwEtlTableWithSource;

import java.time.LocalDate;

/**
 * 目标端能力适配器。
 * 负责目标端建表/更新、运行前准备（如分区）和增量位点查询等能力。
 */
public interface TargetAdapter
{
    /**
     * 目标端类型标识（如 HDFS）。
     */
    String getType();

    boolean addPartition(long taskId, String db, String table, String partName, String partValue);

    boolean createOrUpdateTable(VwEtlTableWithSource etlTable);

    Object getMaxValue(VwEtlTableWithSource table, String columnName, String partValue);

    /**
     * 执行任务前的目标端准备动作（如分区创建）。
     */
    boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, String bizDateValue);

    /**
     * 生成目标端 writer 模板片段。
     */
    String buildWriterJob(VwEtlTableWithSource table);

    /**
     * 生成目标端 writer 模板片段，使用指定日期替代当前业务日期。
     * 用于补数场景，分区路径和日期变量需要按目标日期生成。
     * 默认实现退化为 {@link #buildWriterJob}，有日期依赖的适配器需覆写。
     *
     * @param table     采集表视图对象
     * @param targetDate 目标日期
     * @return writer 模板片段
     */
    default String buildWriterJobForDate(VwEtlTableWithSource table, LocalDate targetDate)
    {
        return buildWriterJob(table);
    }
}
