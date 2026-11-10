package com.wgzhao.addax.admin.alert.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.alert.AlertEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable fact that an alert condition was observed. Delivery rows are created
 * from it in the same transaction (transactional outbox).
 */
@Entity
@Table(name = "alert_event")
@Getter
@Setter
public class AlertEvent
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 255)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "scope_value", length = 255)
    private String scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertEventStatus status;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "fire_count", nullable = false)
    private int fireCount = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
