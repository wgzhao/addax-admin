package com.wgzhao.addax.admin.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRecorder
{
    private final AuditRepository auditRepository;
    private final AuditWriter auditWriter;

    @Value("${HOSTNAME:${spring.application.name:addax-admin}}")
    private String nodeId;

    public void success(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String resourceName,
        Object beforeData,
        Object afterData
    )
    {
        success(action, category, severity, resourceType, resourceId, resourceName,
            beforeData, afterData, Map.of(), null);
    }

    public void success(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String resourceName,
        Object beforeData,
        Object afterData,
        Map<String, Object> metadata
    )
    {
        success(action, category, severity, resourceType, resourceId, resourceName,
            beforeData, afterData, metadata, null);
    }

    public void success(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String resourceName,
        Object beforeData,
        Object afterData,
        Map<String, Object> metadata,
        String actorUsername
    )
    {
        AuditEventCommand.AuditEventCommandBuilder builder = AuditEventCommand.builder()
            .action(action)
            .category(category)
            .severity(severity)
            .resourceType(resourceType)
            .resourceId(resourceId == null ? null : String.valueOf(resourceId))
            .resourceName(resourceName)
            .outcome("SUCCESS")
            .beforeData(beforeData)
            .afterData(afterData)
            .metadata(metadata)
            .actorUsername(actorUsername);
        auditRepository.insert(toData(builder.build(), "SUCCESS"));
    }

    public void accepted(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String resourceName,
        Map<String, Object> metadata
    )
    {
        AuditEventCommand command = AuditEventCommand.builder()
            .action(action)
            .category(category)
            .severity(severity)
            .resourceType(resourceType)
            .resourceId(resourceId == null ? null : String.valueOf(resourceId))
            .resourceName(resourceName)
            .outcome("ACCEPTED")
            .metadata(metadata)
            .build();
        auditRepository.insert(toData(command, "ACCEPTED"));
    }

    public void independentFailure(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String resourceName,
        String actorUsername,
        String errorCode,
        Throwable error,
        Map<String, Object> metadata
    )
    {
        AuditEventCommand command = AuditEventCommand.builder()
            .action(action)
            .category(category)
            .severity(severity)
            .resourceType(resourceType)
            .resourceId(resourceId == null ? null : String.valueOf(resourceId))
            .resourceName(resourceName)
            .outcome("FAILED")
            .actorUsername(actorUsername)
            .errorCode(errorCode)
            .errorMessage(error == null ? null : safeMessage(error))
            .metadata(metadata)
            .build();
        recordIndependently(command);
    }

    public void failed(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String resourceName,
        String errorCode,
        String errorMessage,
        Map<String, Object> metadata
    )
    {
        AuditEventCommand command = AuditEventCommand.builder()
            .action(action)
            .category(category)
            .severity(severity)
            .resourceType(resourceType)
            .resourceId(resourceId == null ? null : String.valueOf(resourceId))
            .resourceName(resourceName)
            .outcome("FAILED")
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .metadata(metadata)
            .build();
        auditRepository.insert(toData(command, "FAILED"));
    }

    public void denied(
        String action,
        String category,
        String severity,
        String resourceType,
        Object resourceId,
        String reason,
        Map<String, Object> metadata
    )
    {
        AuditEventCommand command = AuditEventCommand.builder()
            .action(action)
            .category(category)
            .severity(severity)
            .resourceType(resourceType)
            .resourceId(resourceId == null ? null : String.valueOf(resourceId))
            .outcome("DENIED")
            .errorCode("ACCESS_DENIED")
            .errorMessage(reason)
            .metadata(metadata)
            .build();
        recordIndependently(command);
    }

    private void recordIndependently(AuditEventCommand command)
    {
        try {
            auditWriter.write(toData(command, command.getOutcome()));
        }
        catch (Exception ex) {
            log.error("Failed to persist independent audit event {}", command.getAction(), ex);
        }
    }

    private AuditEventData toData(AuditEventCommand command, String defaultOutcome)
    {
        HttpServletRequest request = currentRequest();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = firstText(command.getActorUsername(),
            authentication == null ? null : authentication.getName(), "SYSTEM");
        List<String> roles = authentication == null || !authentication.isAuthenticated()
            ? List.of()
            : authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority().toLowerCase(Locale.ROOT)
                .replaceFirst("^role_", ""))
            .filter(role -> !"anonymous".equals(role))
            .distinct()
            .toList();
        return new AuditEventData(
            UUID.randomUUID(),
            OffsetDateTime.now(),
            request == null ? null : (String) request.getAttribute(AuditRequestFilter.REQUEST_ID_ATTRIBUTE),
            "SYSTEM".equals(actor) ? "SYSTEM" : "USER",
            actor,
            roles,
            request == null ? null : (String) request.getAttribute(AuditRequestFilter.JWT_ID_ATTRIBUTE),
            command.getAction(),
            command.getCategory() == null ? "CONFIG" : command.getCategory(),
            command.getSeverity() == null ? "INFO" : command.getSeverity(),
            command.getResourceType(),
            command.getResourceId(),
            command.getResourceName(),
            command.getOutcome() == null ? defaultOutcome : command.getOutcome(),
            request == null ? null : request.getMethod(),
            request == null ? null : request.getRequestURI(),
            request == null ? null : (String) request.getAttribute(AuditRequestFilter.CLIENT_IP_ATTRIBUTE),
            request == null ? null : request.getHeader("User-Agent"),
            nodeId(),
            command.getBeforeData(),
            command.getAfterData(),
            null,
            command.getErrorCode(),
            command.getErrorMessage(),
            command.getMetadata()
        );
    }

    private HttpServletRequest currentRequest()
    {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String safeMessage(Throwable error)
    {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String firstText(String... values)
    {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"anonymousUser".equals(value)) {
                return value;
            }
        }
        return "SYSTEM";
    }

    private String nodeId()
    {
        if (nodeId != null && !nodeId.isBlank()) {
            return nodeId;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (Exception ex) {
            return "unknown-node";
        }
    }
}
