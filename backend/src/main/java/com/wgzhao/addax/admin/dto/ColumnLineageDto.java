package com.wgzhao.addax.admin.dto;

/**
 * One field-level lineage edge: a source column flows into a target column of an ODS collection.
 */
public record ColumnLineageDto(
    Long tid,
    String sourceName,
    String sourceDb,
    String sourceTable,
    String sourceColumn,
    String targetName,
    String targetDb,
    String targetTable,
    String targetColumn
)
{
}
