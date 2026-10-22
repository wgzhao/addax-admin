package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record CollectionSchemaPreview(
    List<SourceColumnMetadata> sourceColumns,
    TargetTableMetadata targetTable,
    List<MappingSuggestion> suggestions,
    List<String> unmatchedSourceColumns,
    List<String> unmatchedTargetColumns,
    String targetSchemaFingerprint,
    JsonNode targetOptions,
    List<TargetContractReport.Finding> findings
)
{
    public record MappingSuggestion(
        String sourceColumnName,
        String targetColumnName,
        boolean confirmed,
        String sourceType,
        String targetType,
        TargetContractReport.Finding finding
    )
    {
    }
}
