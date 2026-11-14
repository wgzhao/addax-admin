package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureStage;
import com.wgzhao.addax.admin.model.EtlReconcile;
import com.wgzhao.addax.admin.repository.EtlReconcileRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 采集结果对账：核对 Addax 上报的读取行数与写入行数是否一致。
 *
 * <p>写入行数 = 读取行数 - 错误行数（Addax 上报的 {@code totalReadRecords} /
 * {@code totalErrorRecords}），差异即错误行数——有错误行数说明采集过程中有脏数据/失败行被丢弃，
 * 落地不完整。对账在 job-report 到达时同步执行，不去目标端重新计数。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReconcileService
{
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    /** 默认容差 0：不允许任何错误行数。 */
    public static final long DEFAULT_TOLERANCE = 0L;

    private final EtlReconcileRepo reconcileRepo;
    private final FailureRecorder failureRecorder;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 对一次采集做对账：写入行数 = 读取行数 - 错误行数，差异即错误行数。
     */
    public EtlReconcile reconcile(long tid, Long executionId, LocalDate bizDate,
        long readRecords, long errorRecords)
    {
        long writeRecords = Math.max(0, readRecords - errorRecords);
        EtlReconcile record = new EtlReconcile();
        record.setTid(tid);
        record.setExecutionId(executionId);
        record.setBizDate(bizDate);
        record.setSourceRows(readRecords);
        record.setTargetRows(writeRecords);
        record.setDiff(errorRecords);
        record.setTolerance(DEFAULT_TOLERANCE);
        if (errorRecords <= DEFAULT_TOLERANCE)
        {
            record.setStatus(PASS);
        }
        else
        {
            record.setStatus(FAIL);
            record.setMessage("采集读取 " + readRecords + " 行，写入 " + writeRecords
                + " 行，丢失 " + errorRecords + " 行（错误记录）");
        }
        reconcileRepo.save(record);
        if (FAIL.equals(record.getStatus()))
        {
            recordFailure(record);
        }
        return record;
    }

    /**
     * 手动触发：从 etl_statistic 重新取 read/error 做对账（补历史对账）。该 tid+bizDate 没有
     * 统计记录时返回 {@code null}。
     */
    public EtlReconcile reconcileFromStatistic(long tid, LocalDate bizDate)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select total_recs, total_errors, execution_id from etl_statistic "
                + "where tid = ? and biz_date = ? order by id desc limit 1",
            tid, bizDate);
        if (rows.isEmpty())
        {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Long read = asLong(row.get("total_recs"));
        Long error = asLong(row.get("total_errors"));
        Long executionId = asLong(row.get("execution_id"));
        if (read == null || error == null)
        {
            return null;
        }
        return reconcile(tid, executionId, bizDate, read, error);
    }

    private Long asLong(Object value)
    {
        return value == null ? null : ((Number) value).longValue();
    }

    private void recordFailure(EtlReconcile record)
    {
        failureRecorder.record(record.getTid(), record.getMessage(), FailureEvidence.create()
            .execution(record.getExecutionId())
            .stage(FailureStage.RECONCILE)
            .errorClass("ReconcileMismatch")
            .bizDate(record.getBizDate()));
    }
}
