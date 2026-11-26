package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * Aggregate of one "refresh every table" round (day-cut or manual "refresh all").
 * The counts are what the day-cut event reports: without them a partial refresh
 * was visible only as per-table log lines.
 *
 * <p>{@code failed} counts tables that were actually attempted and did not refresh;
 * {@code skippedSourceDown} counts tables never attempted because an earlier table on the
 * same source could not connect, so {@code total = success + failed + skippedSourceDown}
 * (except on interruption, where the round stops early). {@code sourceUnavailable} is the
 * outage impact across both: the failed attempt plus every skipped table.
 * {@code incrementalPositionMissing} is a subset of {@code failed}: tables an earlier run already
 * collected whose target MAX position could not be read, so they were denied a template (and are
 * left waiting for update) rather than silently reloaded in full.</p>
 */
public record TableRefreshStats(int total, int success, int failed, int sourceUnavailable,
    int skippedSourceDown, int incrementalPositionMissing, int waitSchemaToNotCollect,
    List<Failure> failures)
{
    public record Failure(long tid, String sourceDb, String sourceTable, String reason)
    {
    }

    public static TableRefreshStats empty()
    {
        return new TableRefreshStats(0, 0, 0, 0, 0, 0, 0, List.of());
    }
}
