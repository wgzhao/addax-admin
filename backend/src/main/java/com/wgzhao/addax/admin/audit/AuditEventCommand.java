package com.wgzhao.addax.admin.audit;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class AuditEventCommand
{
    private String action;
    private String category;
    private String severity;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String outcome;
    private String actorUsername;
    private Object beforeData;
    private Object afterData;
    private Map<String, Object> metadata;
    private String errorCode;
    private String errorMessage;
}
