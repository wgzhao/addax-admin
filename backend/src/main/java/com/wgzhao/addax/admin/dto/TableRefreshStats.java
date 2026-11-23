package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * Aggregate of one "refresh every table" round (day-cut or manual "refresh all").
 * The counts are what the day-cut event reports: without them a partial refresh
 * was visible only as per-table log lines.
 */
public record TableRefreshStats(int total, int success, int failed, int sourceUnavailable,
    int waitSchemaToNotCollect, List<Failure> failures)
{
    public record Failure(long tid, String sourceDb, String sourceTable, String reason)
    {
    }

    public static TableRefreshStats empty()
    {
        return new TableRefreshStats(0, 0, 0, 0, 0, List.of());
    }
}
