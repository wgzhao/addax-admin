package com.wgzhao.addax.admin.alert.dto;

import java.time.Instant;

public record AlertEventDto(
    Long id,
    String eventKey,
    String eventType,
    String severity,
    String scopeType,
    String scopeValue,
    String status,
    Instant firstSeen,
    Instant lastSeen,
    int fireCount,
    Instant resolvedAt,
    String title,
    String text
)
{
}
