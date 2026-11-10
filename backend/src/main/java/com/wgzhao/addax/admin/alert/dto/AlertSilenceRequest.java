package com.wgzhao.addax.admin.alert.dto;

import java.time.Instant;

public record AlertSilenceRequest(
    String scopeType,
    String scopeValue,
    Instant startsAt,
    Instant endsAt,
    String reason
)
{
}
