package com.wgzhao.addax.admin.dto;

/**
 * A field the user declares when sampling cannot see it (empty collection, rare field, or an
 * intentional override of the inferred type).
 */
public record ManualSourceColumnRequest(
    String sourceName,
    String sourceType,
    String targetName,
    Integer dataLength,
    Integer dataPrecision,
    Integer dataScale,
    String comment
)
{
}
