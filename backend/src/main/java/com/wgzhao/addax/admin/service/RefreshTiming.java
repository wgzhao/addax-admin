package com.wgzhao.addax.admin.service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Wall-clock counters for refreshing one collection table.
 *
 * <p>A refresh round costs almost nothing locally: it is dominated by round trips to the source
 * database and the target (Hive/HDFS, JDBC, Doris). Those round trips are spread across several
 * layers, and the heaviest one — the target MAX lookup — happens deep inside the job builder, so
 * the counters live in a thread-local instead of being threaded through every call.
 *
 * <p>Nothing is allocated until {@link #begin()} runs, so ordinary job builds (manual refresh,
 * fillback) pay nothing beyond a null check.</p>
 */
public final class RefreshTiming
{
    public enum Stage
    {
        /** Loading the table view row itself. */
        VIEW,
        /** Source schema inspection, including the source connection. */
        SOURCE_COLUMNS,
        /** Target DDL or external contract check, including the target connection. */
        TARGET,
        /** Job template generation and persistence; includes {@link #MAX_VALUE}. */
        JOB,
        /** Target MAX lookup inside the job builder (a subset of {@link #JOB}). */
        MAX_VALUE
    }

    private static final ThreadLocal<EnumMap<Stage, Long>> CURRENT = new ThreadLocal<>();

    private RefreshTiming()
    {
    }

    public static void begin()
    {
        CURRENT.set(new EnumMap<>(Stage.class));
    }

    /** Adds to the current table's counter; a no-op outside a refresh. */
    public static void add(Stage stage, long nanos)
    {
        EnumMap<Stage, Long> counters = CURRENT.get();
        if (counters == null || nanos <= 0) {
            return;
        }
        counters.merge(stage, nanos, Long::sum);
    }

    public static Map<Stage, Long> snapshot()
    {
        EnumMap<Stage, Long> counters = CURRENT.get();
        return counters == null ? Map.of() : new EnumMap<>(counters);
    }

    public static void end()
    {
        CURRENT.remove();
    }
}
