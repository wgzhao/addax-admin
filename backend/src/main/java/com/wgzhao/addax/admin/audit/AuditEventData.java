package com.wgzhao.addax.admin.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

record AuditEventData(
    UUID eventId,
    OffsetDateTime occurredAt,
    String requestId,
    String actorType,
    String actorUsername,
    List<String> actorRoles,
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
    Object beforeData,
    Object afterData,
    Object changedFields,
    String errorCode,
    String errorMessage,
    Object metadata
)
{
}
