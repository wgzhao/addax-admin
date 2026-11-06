package com.wgzhao.addax.admin.dto;

import java.util.Map;

/**
 * Structured source-type evidence for one sampled field.
 *
 * <p>{@code types} holds the non-null BSON type labels and their counts in the sample; {@code
 * nullCount} and {@code missing} are kept separate so the UI never has to guess what the remaining
 * percentage means. This is display/diagnostic metadata and deliberately does not become a target
 * column comment.</p>
 */
public record SourceFieldMeta(
    int sampleSize,
    int present,
    int missing,
    int nullCount,
    Map<String, Integer> types
)
{
}
