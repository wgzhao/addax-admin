package com.wgzhao.addax.admin.alert;

/**
 * Event types wired to a publisher in phase 1. The remaining enterprise types
 * (missing partition, SLA, queue backlog, node offline, schema drift, digest)
 * are reserved for later phases; adding them here without a publisher would only
 * let operators create rules that never fire.
 */
public enum AlertEventType
{
    COLLECT_FAILURE(AlertSeverity.P1),
    COLLECT_RECOVERY(AlertSeverity.P2),
    SECURITY(AlertSeverity.P1),
    SYSTEM(AlertSeverity.P2);

    private final AlertSeverity defaultSeverity;

    AlertEventType(AlertSeverity defaultSeverity)
    {
        this.defaultSeverity = defaultSeverity;
    }

    public AlertSeverity defaultSeverity()
    {
        return defaultSeverity;
    }
}
