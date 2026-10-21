package com.wgzhao.addax.admin.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditEventDto(
    long id,
    UUID eventId,
    Instant occurredAt,
    String requestId,
    String actorType,
    String actorUsername,
    JsonNode actorRoles,
    String sessionId,
    String action,
    String category,
    String severity,
    String resourceType,
    String resourceId,
    String resourceName,
    String outcome,
    String httpMethod,
    String requestPath,
    String clientIp,
    String userAgent,
    String nodeId,
    JsonNode beforeData,
    JsonNode afterData,
    JsonNode changedFields,
    String errorCode,
    String errorMessage,
    JsonNode metadata
)
{
}
