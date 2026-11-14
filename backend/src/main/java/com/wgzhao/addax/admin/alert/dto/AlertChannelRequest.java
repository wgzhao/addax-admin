package com.wgzhao.addax.admin.alert.dto;

import tools.jackson.databind.JsonNode;

public record AlertChannelRequest(
    String name,
    String type,
    JsonNode config,
    Boolean enabled,
    Integer rateLimitPerMinute
)
{
}
