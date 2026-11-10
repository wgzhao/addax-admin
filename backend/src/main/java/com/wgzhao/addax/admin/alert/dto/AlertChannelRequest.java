package com.wgzhao.addax.admin.alert.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AlertChannelRequest(
    String name,
    String type,
    JsonNode config,
    Boolean enabled,
    Integer rateLimitPerMinute
)
{
}
