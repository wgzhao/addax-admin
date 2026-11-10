package com.wgzhao.addax.admin.alert.dto;

import java.time.Instant;
import java.util.List;

public record AlertRuleDto(
    Long id,
    String name,
    String eventType,
    String severity,
    String scopeType,
    String scopeValue,
    List<Long> channelIds,
    int cooldownSecs,
    int groupWindowSecs,
    boolean ackRequired,
    Integer escalateAfterSecs,
    List<Long> escalationChannelIds,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
)
{
}
