package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Channel-neutral rendering of an alert. Providers pick the representation they
 * can carry: IM channels use markdown, webhooks get the structured data.
 */
public record AlertMessage(
    String eventKey,
    String eventType,
    AlertSeverity severity,
    String title,
    String markdown,
    String text,
    String html,
    JsonNode data,
    List<String> recipients,
    Instant occurredAt,
    Map<String, String> links
)
{
}
