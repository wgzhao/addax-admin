package com.wgzhao.addax.admin.service.source;

import java.util.List;

/**
 * Result of a metadata discovery. Warnings are user-facing: sampling misses, mixed types, truncation.
 */
public record SourceSchema(
    List<SourceField> fields,
    List<String> warnings,
    long sampledDocuments,
    boolean truncated
)
{
    public SourceSchema
    {
        fields = fields == null ? List.of() : List.copyOf(fields);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
