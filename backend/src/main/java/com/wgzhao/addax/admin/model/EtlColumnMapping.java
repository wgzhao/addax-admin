package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Stable target-column contract for one source column.
 */
@Entity
@Table(name = "etl_column_mapping")
@Data
public class EtlColumnMapping
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid", nullable = false)
    private Long tid;

    @Column(name = "source_column_name", nullable = false, length = 255)
    private String sourceColumnName;

    @Column(name = "target_column_name", nullable = false, length = 255)
    private String targetColumnName;

    @Column(name = "ordinal_no", nullable = false)
    private Integer ordinalNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist()
    {
        LocalDateTime now = LocalDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate()
    {
        updatedAt = LocalDateTime.now();
    }
}
