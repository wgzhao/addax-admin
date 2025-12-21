package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "etl_job_queue", schema = "public",
    indexes = {
        @Index(name = "idx_etl_job_queue_status_available", columnList = "status,available_at,priority"),
        @Index(name = "idx_etl_job_queue_lease", columnList = "status,lease_until")
    })
public class EtlJobQueue
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tid;

    @Column(name = "biz_date")
    private LocalDate bizDate;

    @Column(name = "part_name")
    private String partName;

    // optional payload snapshot
    @Column(columnDefinition = "jsonb")
    private String payload;

    private Integer priority;

    private String status; // pending, running, completed, failed, cancelled

    @Column(name = "available_at")
    private Instant availableAt;

    private Integer attempts;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at")
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;
}
