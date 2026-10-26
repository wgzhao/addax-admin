package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Read-only preview of what a collection definition will create or validate. A batch response keeps
 * this shape per table so one bad target never hides the result of the other selected tables.
 */
public record CollectionPlanPreview(
    String sourceDb,
    String sourceTable,
    String targetDb,
    String targetTable,
    String schemaManagementMode,
    String status,
    String action,
    String summary,
    List<SourceColumnMetadata> sourceColumns,
    TargetTableMetadata actualTargetTable,
    List<PlannedTargetColumn> plannedColumns,
    List<TargetTableMetadata.TargetPartition> plannedPartitions,
    List<CollectionSchemaPreview.MappingSuggestion> mappings,
    String targetSchemaFingerprint,
    JsonNode targetOptions,
    String planDdl,
    List<TargetContractReport.Finding> findings
)
{
    public record PlannedTargetColumn(
        String name,
        String sourceColumnName,
        String type,
        boolean nullable,
        boolean primaryKey,
        String comment,
        String role,
        int ordinal
    )
    {
    }
}
