package com.wgzhao.addax.admin.service.source;

/**
 * What a source engine can actually express to Addax. Generic code asks these flags instead of
 * branching on the database name.
 */
public record SourceCapabilities(
    FilterFormat filterFormat,
    boolean splitPk,
    boolean autoPk,
    boolean nullLiteralColumns,
    boolean constantColumns,
    boolean autoDeleteMissingColumns,
    boolean caseSensitiveIdentifiers,
    boolean samplingInference,
    String readerPlugin
)
{
}
