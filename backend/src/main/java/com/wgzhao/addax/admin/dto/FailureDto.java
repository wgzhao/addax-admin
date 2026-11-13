package com.wgzhao.addax.admin.dto;

/**
 * Failure fact as exposed to the UI. {@code detail} is null when the caller may not view
 * operational evidence; the summary is always present.
 */
public record FailureDto(
    long id,
    long tid,
    Long executionId,
    Long jobQueueId,
    Long logId,
    String logName,
    String occurredAt,
    String stage,
    String category,
    String errorClass,
    String errorCode,
    String summary,
    String detail,
    Integer attemptNo,
    Integer maxAttempts,
    boolean finalAttempt,
    String runType,
    String bizDate,
    Integer sourceId,
    String sourceDb,
    String sourceTable,
    String targetDb,
    String targetTable
)
{
}
