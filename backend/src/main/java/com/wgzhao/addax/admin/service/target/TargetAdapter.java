package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.model.EtlTarget;
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

    /**
     * 目标族。调用方用它决定表结构/属性展示，不再比较 {@link #getType()} 字符串。
     */
    TargetFamily getFamily();

    /**
     * Validate a target endpoint before it is saved. Default: nothing beyond connectivity parsing.
     */
    default void validateForSave(EtlTarget target)
    {
    }

    /**
     * Whether the platform may create and evolve target tables. EXTERNAL mode is always available for
     * an adapter that supports its contract check; this flag only governs managed DDL.
     */
    default boolean supportsManagedSchema()
    {
        return true;
    }

    /**
     * Test the connection described by the target payload (saved or unsaved).
     */
    default void testConnection(EtlTarget target)
    {
        throw new UnsupportedOperationException("Target type " + getType() + " does not support connection tests");
    }

    /**
     * Whether this target carries a per-day value: HDFS uses its partition column, RDBMS uses the
     * snapshot business date column. Generic code asks this capability instead of reading Hive fields,
     * so a target without a date dimension is never treated as if it had one.
     */
    default boolean hasDateDimension(VwEtlTableWithSource table)
    {
        return false;
    }

    boolean createOrUpdateTable(VwEtlTableWithSource etlTable);

    /**
     * Read the actual target contract without changing it.
     */
    TargetTableMetadata inspectTable(VwEtlTableWithSource etlTable);

    Object getMaxValue(VwEtlTableWithSource table, String columnName, String partValue);

    /**
     * Called once before a refresh round walks every table. An adapter that pays a per-call
     * connection or warm-up cost may open one connection per endpoint here and reuse it until
     * {@link #endRefreshRound()}. Default: nothing to prepare.
     */
    default void beginRefreshRound()
    {
    }

    /**
     * Called after the refresh round, on the same thread, even when the round failed or was
     * interrupted. Every connection opened by {@link #beginRefreshRound()} must be closed here.
     */
    default void endRefreshRound()
    {
    }

    /**
     * 执行任务前的目标端准备动作（如分区创建）。
     *
     * <p>失败时返回带阶段和原因的结果，由调用方记录唯一一条失败事实。适配器自己只更新
     * etl_jour 操作日志，不再另写失败事实，否则一次逻辑失败会变成两条记录。</p>
     *
     * @param executionId 当前执行 ID，用于把准备阶段的流水和失败事实关联到同一次执行
     */
    TargetPreparationResult prepareBeforeRun(long taskId, Long executionId,
        VwEtlTableWithSource table, LocalDate bizDate);

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
