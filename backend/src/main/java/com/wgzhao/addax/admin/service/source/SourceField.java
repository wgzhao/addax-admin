package com.wgzhao.addax.admin.service.source;

import com.wgzhao.addax.admin.dto.SourceFieldMeta;

/**
 * One source field discovered by an adapter.
 *
 * <p>{@code sourceType} is the portable logical type stored in {@code etl_column.source_type}; it must
 * be a name the target type mapping understands. {@code nativeType} is the engine's own spelling and
 * is for display/diagnostics only. {@code comment} is the short business/target comment; sampled type
 * counts live in {@code sourceMeta} and must not be written into a target COMMENT.</p>
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
    String suggestedTargetName,
    SourceFieldMeta sourceMeta
)
{
}
