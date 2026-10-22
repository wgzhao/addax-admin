package com.wgzhao.addax.admin.dto;

import java.time.LocalDateTime;

public record ColumnMappingDto(
    Long id,
    String sourceColumnName,
    String targetColumnName,
    int ordinalNo,
    boolean sourceActive,
    String sourceType,
    String targetType,
    String targetTypeFull,
    String comment,
    LocalDateTime updatedAt
)
{
}
