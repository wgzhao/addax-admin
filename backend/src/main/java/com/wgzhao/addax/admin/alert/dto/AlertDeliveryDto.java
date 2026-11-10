package com.wgzhao.addax.admin.alert.dto;

import java.time.Instant;

public record AlertDeliveryDto(
    Long id,
    Long eventId,
    String eventTitle,
    String eventType,
    String severity,
    Long channelId,
    String channelName,
    String status,
    int attempts,
    Instant nextRetryAt,
    Integer responseCode,
    String responseBody,
    String lastError,
    Instant sentAt,
    Instant createdAt,
    Instant updatedAt
)
{
}
