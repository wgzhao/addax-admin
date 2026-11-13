package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One observed failure of one operation on one collection definition.
 *
 * <p>This is an append-only fact, not a status column and not a copy of the raw log. It points at
 * the execution and the log that produced it; either can expire without deleting the failure.</p>
 */
@Entity
@Table(name = "etl_failure")
@Data
public class EtlFailure
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid", nullable = false)
    private Long tid;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "job_queue_id")
    private Long jobQueueId;

    /**
     * addax_log.id, intentionally without a foreign key: raw logs are purged on a shorter schedule.
     */
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "log_name", length = 255)
    private String logName;

    @Column(name = "occurred_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime occurredAt;

    @Column(name = "stage", nullable = false, length = 32)
    private String stage;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "error_class", length = 200)
    private String errorClass;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @Column(name = "detail")
    private String detail;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "final_attempt", nullable = false)
    private boolean finalAttempt = true;

    @Column(name = "run_type", length = 16)
    private String runType;

    @Column(name = "biz_date")
    private LocalDate bizDate;

    @Column(name = "source_id")
    private Integer sourceId;

    @Column(name = "source_db", length = 64)
    private String sourceDb;

    @Column(name = "source_table", length = 255)
    private String sourceTable;

    @Column(name = "target_db", length = 64)
    private String targetDb;

    @Column(name = "target_table", length = 255)
    private String targetTable;

    /**
     * Set only by the one-time backfill of legacy etl_jour failures; keeps the migration idempotent.
     */
    @Column(name = "legacy_jour_id")
    private Long legacyJourId;
}
