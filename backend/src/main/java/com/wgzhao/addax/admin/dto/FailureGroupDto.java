package com.wgzhao.addax.admin.dto;

/**
 * Repeated occurrences of one fingerprint for one table.
 */
public record FailureGroupDto(
    String fingerprint,
    long tid,
    String stage,
    String category,
    String summary,
    long count,
    String firstSeenAt,
    String lastSeenAt,
    String sourceDb,
    String sourceTable,
    String targetDb,
    String targetTable
)
{
}
