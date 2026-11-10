package com.wgzhao.addax.admin.alert.dto;

import java.util.List;

public record AlertRuleRequest(
    String name,
    String eventType,
    String severity,
    String scopeType,
    String scopeValue,
    List<Long> channelIds,
    Integer cooldownSecs,
    Integer groupWindowSecs,
    Boolean ackRequired,
    Integer escalateAfterSecs,
    List<Long> escalationChannelIds,
    Boolean enabled
)
{
}
