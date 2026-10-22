package com.wgzhao.addax.admin.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CollectionSchemaPreviewRequest(
    Integer sourceId,
    String sourceDb,
    String sourceTable,
    Long targetId,
    String targetDb,
    String targetTable,
    String schemaManagementMode,
    JsonNode targetOptions
)
{
}
