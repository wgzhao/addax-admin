package com.wgzhao.addax.admin.alert;

/**
 * Event types wired to a publisher. The remaining enterprise types (missing
 * partition, SLA, queue backlog, node offline, schema drift) are reserved for
 * later phases; adding them here without a publisher would only let operators
 * create rules that never fire.
 *
 * <p>{@link #SCHEDULE} exists separately from {@link #SYSTEM} because rules
 * route by event type: a day-cut failure must be able to reach the on-call
 * channel without also dragging every ad-hoc operational message there.
 * {@link #DAILY} is informational and, exceptionally, is delivered only when an
 * enabled rule for this type exists — see {@link AlertDispatcher}.</p>
 */
public enum AlertEventType
{
    COLLECT_FAILURE(AlertSeverity.P1),
    COLLECT_RECOVERY(AlertSeverity.P2),
    SECURITY(AlertSeverity.P1),
    SYSTEM(AlertSeverity.P2),
    SCHEDULE(AlertSeverity.P1),
    DAILY(AlertSeverity.P3);

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
