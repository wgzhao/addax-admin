package com.wgzhao.addax.admin.alert.dto;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record AlertChannelDto(
    Long id,
    String name,
    String type,
    String typeLabel,
    boolean enabled,
    JsonNode config,
    int rateLimitPerMinute,
    Instant lastTestAt,
    Boolean lastTestOk,
    String createdBy,
    Instant createdAt,
    Instant updatedAt
)
{
}
