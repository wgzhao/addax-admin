package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统级标志位，用于跨实例协调一些全局操作（如 schema refresh）
 */
@Entity
@Table(name = "system_flag")
@Data
public class SystemFlag {
    @Id
    @Column(name = "flag_key", length = 128)
    private String flagKey;

    @Column(name = "flag_value", length = 128)
    private String flagValue;

    @Column(name = "last_started_at")
    private LocalDateTime lastStartedAt;

    @Column(name = "last_finished_at")
    private LocalDateTime lastFinishedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
