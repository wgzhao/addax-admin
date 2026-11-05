package com.wgzhao.addax.admin.service.source;

/**
 * One source field discovered by an adapter.
 *
 * <p>{@code sourceType} is the portable logical type stored in {@code etl_column.source_type}; it must
 * be a name the target type mapping understands. {@code nativeType} is the engine's own spelling and
 * is for display/diagnostics only.</p>
 */
public record SourceField(
    String name,
    String sourceType,
    String nativeType,
    int dataLength,
    Integer dataPrecision,
    Integer dataScale,
    String comment,
    boolean nullable,
    boolean primaryKey,
    String suggestedTargetName
)
{
}
