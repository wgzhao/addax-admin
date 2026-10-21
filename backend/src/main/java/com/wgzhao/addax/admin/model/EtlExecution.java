package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable attempt to execute a collection definition.
 */
@Entity
@Table(name = "etl_execution")
@Data
public class EtlExecution
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid", nullable = false)
    private Long tid;

    @Column(name = "job_queue_id")
    private Long jobQueueId;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "source_db", length = 64)
    private String sourceDb;

    @Column(name = "source_table", length = 255)
    private String sourceTable;

    @Column(name = "source_code", length = 50)
    private String sourceCode;

    @Column(name = "source_name", length = 200)
    private String sourceName;

    @Column(name = "target_db", length = 64)
    private String targetDb;

    @Column(name = "target_table", length = 255)
    private String targetTable;

    @Column(name = "target_code", length = 50)
    private String targetCode;

    @Column(name = "target_name", length = 200)
    private String targetName;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "snapshot_version", nullable = false)
    private Integer snapshotVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_snapshot", columnDefinition = "jsonb", nullable = false)
    private JsonNode configSnapshot;

    @Column(name = "job_digest", length = 64)
    private String jobDigest;

    @Column(name = "error_message")
    private String errorMessage;
}
