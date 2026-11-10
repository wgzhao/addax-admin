package com.wgzhao.addax.admin.alert.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alert_rule")
@Getter
@Setter
public class AlertRule
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "scope_value", length = 255)
    private String scopeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channel_ids", nullable = false, columnDefinition = "jsonb")
    private List<Long> channelIds = new ArrayList<>();

    @Column(name = "cooldown_secs", nullable = false)
    private int cooldownSecs = 300;

    @Column(name = "group_window_secs", nullable = false)
    private int groupWindowSecs = 60;

    /**
     * Reserved for the phase-3 acknowledgement/escalation worker; phase 1 only
     * persists the values so adding the worker does not require a migration.
     */
    @Column(name = "ack_required", nullable = false)
    private boolean ackRequired;

    @Column(name = "escalate_after_secs")
    private Integer escalateAfterSecs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "escalation_channel_ids", columnDefinition = "jsonb")
    private List<Long> escalationChannelIds;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
