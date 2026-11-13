package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureStage;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Optional evidence attached to a failure. A fluent builder keeps the many call sites readable;
 * unset fields are stored as null and shown as unavailable by the read API.
 */
public final class FailureEvidence
{
    private Long executionId;
    private Long jobQueueId;
    private Long logId;
    private String logName;
    private LocalDateTime occurredAt;
    private String stage;
    private String errorClass;
    private String errorCode;
    private String detail;
    private Integer attemptNo;
    private Integer maxAttempts;
    private Boolean finalAttempt;
    private String runType;
    private LocalDate bizDate;
    private Long legacyJourId;

    public static FailureEvidence create()
    {
        return new FailureEvidence();
    }

    public FailureEvidence execution(Long value)
    {
        this.executionId = value;
        return this;
    }

    public FailureEvidence queue(Long value)
    {
        this.jobQueueId = value;
        return this;
    }

    public FailureEvidence log(Long id, String name)
    {
        this.logId = id;
        this.logName = name;
        return this;
    }

    public FailureEvidence occurredAt(LocalDateTime value)
    {
        this.occurredAt = value;
        return this;
    }

    public FailureEvidence stage(String value)
    {
        this.stage = value;
        return this;
    }

    public FailureEvidence errorClass(String value)
    {
        this.errorClass = value;
        return this;
    }

    public FailureEvidence errorCode(String value)
    {
        this.errorCode = value;
        return this;
    }

    public FailureEvidence detail(String value)
    {
        this.detail = value;
        return this;
    }

    public FailureEvidence attempt(Integer no, Integer max)
    {
        this.attemptNo = no;
        this.maxAttempts = max;
        return this;
    }

    public FailureEvidence finalAttempt(Boolean value)
    {
        this.finalAttempt = value;
        return this;
    }

    public FailureEvidence runType(String value)
    {
        this.runType = value;
        return this;
    }

    public FailureEvidence bizDate(LocalDate value)
    {
        this.bizDate = value;
        return this;
    }

    /**
     * Marks a fact imported from a legacy {@code etl_jour} row; keeps the backfill idempotent.
     */
    public FailureEvidence legacyJourId(Long value)
    {
        this.legacyJourId = value;
        return this;
    }

    public Long executionId() { return executionId; }

    public Long jobQueueId() { return jobQueueId; }

    public Long logId() { return logId; }

    public String logName() { return logName; }

    public LocalDateTime occurredAt() { return occurredAt; }

    public boolean hasStage() { return stage != null && !stage.isBlank(); }

    public String stage() { return stage == null || stage.isBlank() ? FailureStage.PREPARE : stage; }

    public String errorClass() { return errorClass; }

    public String errorCode() { return errorCode; }

    public String detail() { return detail; }

    public Integer attemptNo() { return attemptNo; }

    public Integer maxAttempts() { return maxAttempts; }

    public Boolean finalAttempt() { return finalAttempt; }

    public String runType() { return runType; }

    public LocalDate bizDate() { return bizDate; }

    public Long legacyJourId() { return legacyJourId; }
}
