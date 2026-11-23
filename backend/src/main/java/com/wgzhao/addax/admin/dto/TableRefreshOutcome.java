package com.wgzhao.addax.admin.dto;

/**
 * Result of refreshing one table's resources, with the failure kind kept apart
 * from the human-readable message. The day-cut summary needs to count source
 * outages separately from schema/DDL failures without matching on error text.
 */
public record TableRefreshOutcome(TaskResultDto result, FailureKind failure)
{
    public enum FailureKind
    {
        NONE,
        INVALID_TABLE,
        INTERRUPTED,
        VIEW_MISSING,
        SOURCE_UNAVAILABLE,
        COLUMNS_FAILED,
        TARGET_CONTRACT_BLOCKED,
        TARGET_DDL_FAILED,
        JOB_CONTENT_FAILED
    }
}
