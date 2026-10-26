package com.wgzhao.addax.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetServiceWithHiveImplTest
{
    @Test
    void removesPartitionColumnRepeatedInTheDescribeColumnSection()
    {
        var description = TargetServiceWithHiveImpl.parseHiveDescription(List.of(
            row("id", "decimal(19,0)", ""),
            row("update_time", "string", "更新时间"),
            // Hive's JDBC driver repeats partition columns before the separator row.
            row("logdate", "string", ""),
            row("", null, null),
            row("# Partition Information", null, null),
            row("# col_name", "data_type", "comment"),
            row("logdate", "string", "")
        ));

        assertEquals(List.of("id", "update_time"), List.copyOf(description.columns().keySet()));
        assertEquals(List.of("logdate"), description.partitions().stream()
            .map(partition -> partition.name())
            .toList());
    }

    private static TargetServiceWithHiveImpl.HiveDescriptionRow row(
        String name, String type, String comment)
    {
        return new TargetServiceWithHiveImpl.HiveDescriptionRow(name, type, comment);
    }
}
