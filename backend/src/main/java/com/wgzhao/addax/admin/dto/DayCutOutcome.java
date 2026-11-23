package com.wgzhao.addax.admin.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Result of one day-cut run. The previous implementation caught every failure and
 * returned void, so the scheduler always reported success, the retry path was dead
 * code, and nothing outside the log could tell a partial refresh from a good one.
 */
public record DayCutOutcome(
    LocalDate bizDate,
    Instant startedAt,
    long durationMs,
    int timeoutSeconds,
    Stage stage,
    boolean success,
    boolean timedOut,
    String error,
    boolean skipped,
    String skipReason,
    int resetTables,
    TableRefreshStats refresh,
    boolean queueMonitorRestarted,
    boolean doneMarkerWritten)
{
    public enum Stage
    {
        NONE, RESET_FLAGS, LOAD_CONFIG, REFRESH_SCHEMA, TRUNCATE_QUEUE
    }

    public static DayCutOutcome skipped(LocalDate bizDate, Instant startedAt, int timeoutSeconds, String reason)
    {
        return new DayCutOutcome(bizDate, startedAt, 0, timeoutSeconds, Stage.NONE, false, false, null,
            true, reason, 0, TableRefreshStats.empty(), true, false);
    }
}
