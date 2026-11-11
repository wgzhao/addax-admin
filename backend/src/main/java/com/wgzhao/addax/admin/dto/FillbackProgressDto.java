package com.wgzhao.addax.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Progress of one fillback batch.
 *
 * <p>The batch is identified by the {@code batch_id} stored in each queue row's payload. A batch
 * row only knows its requested dates, so dates that were deduplicated at submission time (an
 * active queue row already existed for that table/day) or whose queue rows were removed by the
 * daily cleanup are reported as {@code unresolved} instead of being silently omitted.
 *
 * @param found          whether any queue row of the batch still exists
 * @param batchId        batch id, null when no batch was found
 * @param tid            table id
 * @param table          target table label
 * @param supported      whether the table has a date dimension and can be fillbacked
 * @param reason         why the table cannot be fillbacked, null when supported
 * @param retired        whether the collection definition is retired
 * @param sourceEnabled  whether the source endpoint is enabled
 * @param tableStatus    current table status code
 * @param maxDate        latest date that may be backfilled (the system business date)
 * @param startDate      first requested date, null when no batch metadata is available
 * @param endDate        last requested date, null when no batch metadata is available
 * @param submitter      user that submitted the batch
 * @param submittedAt    submission time
 * @param requestedCount number of requested dates
 * @param state          overall batch state
 * @param counts         status counters
 * @param items          per-date detail, ordered by date
 */
public record FillbackProgressDto(
    boolean found,
    String batchId,
    long tid,
    String table,
    boolean supported,
    String reason,
    boolean retired,
    boolean sourceEnabled,
    String tableStatus,
    LocalDate maxDate,
    LocalDate startDate,
    LocalDate endDate,
    String submitter,
    Instant submittedAt,
    int requestedCount,
    String state,
    Counts counts,
    List<Item> items
)
{
    /** Status counters. {@code unresolved} are requested dates without a queue row. */
    public record Counts(
        int total,
        int pending,
        int running,
        int completed,
        int failed,
        int cancelled,
        int unresolved
    )
    {
    }

    /**
     * One requested date.
     *
     * <p>{@code status} is the queue status or {@code unresolved} when no queue row exists.
     * Execution timestamps come from the latest execution attempt of the queue row; the queue
     * row's own updated_at is not refreshed by the terminal update statements.
     */
    public record Item(
        LocalDate date,
        String status,
        int attempts,
        int maxAttempts,
        String lastError,
        Long queueId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
    )
    {
    }
}
