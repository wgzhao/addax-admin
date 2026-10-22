package com.wgzhao.addax.admin.dto;

import com.wgzhao.addax.admin.model.EtlTable;

import java.util.List;

public record CollectionPlanCommitItem(
    EtlTable table,
    List<ColumnMappingInput> mappings,
    String targetSchemaFingerprint
)
{
}
