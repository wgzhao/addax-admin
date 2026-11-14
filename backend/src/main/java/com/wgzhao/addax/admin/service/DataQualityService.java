package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * ODS data-quality assertions run at collection-report time.
 *
 * <p>MVP is row-count drift: a table that collected zero rows (or a fraction of its recent baseline)
 * is almost always a silent collection break, not a business fluctuation. The check is best-effort —
 * it only records a {@code DATA_QUALITY} failure fact and never changes the collection outcome.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataQualityService
{
    private static final int BASELINE_DAYS = 7;
    /** A drop below this fraction of the recent baseline is treated as a probable missed collection. */
    private static final double DROP_RATIO = 0.05;

    private final JdbcTemplate jdbcTemplate;
    private final FailureRecorder failureRecorder;

    public void checkRowCountDrift(long tid, Long executionId, LocalDate bizDate, long totalRecs)
    {
        String reason = driftReason(tid, bizDate, totalRecs);
        if (reason == null)
        {
            return;
        }
        failureRecorder.record(tid, reason, FailureEvidence.create()
            .execution(executionId)
            .stage(FailureStage.DATA_QUALITY)
            .errorClass("RowCountDrift")
            .bizDate(bizDate));
    }

    private String driftReason(long tid, LocalDate bizDate, long totalRecs)
    {
        List<Long> history = jdbcTemplate.query(
            "select total_recs from etl_statistic where tid = ? and biz_date < ? "
                + "and total_recs > 0 order by biz_date desc limit ?",
            (rs, i) -> rs.getLong(1), tid, bizDate, BASELINE_DAYS);
        if (history.isEmpty())
        {
            return null;
        }
        double baseline = history.stream().mapToLong(Long::longValue).average().orElse(0);
        if (baseline <= 0)
        {
            return null;
        }
        if (totalRecs <= 0)
        {
            return "采集 " + totalRecs + " 行，近 " + history.size() + " 天基线均值 "
                + (long) baseline + " 行，疑似空采";
        }
        if ((double) totalRecs < baseline * DROP_RATIO)
        {
            return "采集 " + totalRecs + " 行，不足近 " + history.size() + " 天基线均值 "
                + (long) baseline + " 行的 " + (int) (DROP_RATIO * 100) + "%，疑似漏采";
        }
        return null;
    }
}
