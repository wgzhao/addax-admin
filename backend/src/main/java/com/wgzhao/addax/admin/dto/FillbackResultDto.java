package com.wgzhao.addax.admin.dto;

import java.util.List;

/**
 * Fillback (补数) result.
 *
 * @param totalEnqueued number of tasks successfully enqueued
 * @param skipped       number of tasks skipped (already pending/running)
 * @param details       per-table breakdown
 */
public record FillbackResultDto(
    int totalEnqueued,
    int skipped,
    List<Detail> details
)
{
    /**
     * Per-table fillback detail.
     *
     * @param tid       table ID
     * @param table     table name (target_db.target_table)
     * @param dates     dates attempted
     * @param enqueued  number of tasks enqueued for this table
     * @param skipped   whether this table was skipped entirely (e.g. non-partitioned)
     * @param reason    skip reason if applicable
     */
    public record Detail(
        long tid,
        String table,
        List<String> dates,
        int enqueued,
        boolean skipped,
        String reason
    )
    {
    }
}
