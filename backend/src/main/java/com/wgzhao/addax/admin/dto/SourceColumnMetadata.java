package com.wgzhao.addax.admin.dto;

public record SourceColumnMetadata(
    String name,
    String type,
    int size,
    Integer precision,
    Integer scale,
    String comment,
    boolean nullable,
    boolean primaryKey
)
{
}
