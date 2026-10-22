package com.wgzhao.addax.admin.dto;

import java.util.List;
import java.util.Set;

public record TargetTableMetadata(
    boolean exists,
    List<TargetColumn> columns,
    List<TargetPartition> partitions,
    Set<String> primaryKeys,
    Set<String> uniqueKeys
)
{
    public static TargetTableMetadata missing()
    {
        return new TargetTableMetadata(false, List.of(), List.of(), Set.of(), Set.of());
    }

    public record TargetColumn(
        String name,
        String type,
        int size,
        int scale,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        int ordinal
    )
    {
    }

    public record TargetPartition(String name, String type)
    {
    }
}
