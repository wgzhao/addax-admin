package com.wgzhao.addax.admin.service.source;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source metadata the target-side DDL needs: primary key, NOT NULL columns and the raw type spelling
 * that only the source dialect can provide (MySQL ENUM/SET, unsigned, exact length).
 */
public record SourceTableHints(
    List<String> primaryKeys,
    Set<String> notNull,
    Map<String, String> verbatimTypes
)
{
    public static final SourceTableHints EMPTY = new SourceTableHints(List.of(), Set.of(), Map.of());
}
