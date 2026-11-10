package com.wgzhao.addax.admin.alert.dto;

import java.time.Instant;

public record AlertSilenceDto(
    Long id,
    String scopeType,
    String scopeValue,
    Instant startsAt,
    Instant endsAt,
    String reason,
    String createdBy,
    Instant createdAt,
    boolean active
)
{
}
