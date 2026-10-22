package com.wgzhao.addax.admin.dto;

public record ColumnMappingInput(
    String sourceColumnName,
    String targetColumnName,
    Integer ordinalNo
)
{
}
