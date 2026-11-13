package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureStage;
import com.wgzhao.addax.admin.utils.FailureText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One-time import of failures that only ever existed in {@code etl_jour}.
 *
 * <p>The import runs in bounded batches and goes through {@link FailureRecorder}, so a legacy row
 * gets the same short summary, bounded excerpt and category as a live failure instead of copying a
 * multi-megabyte raw log into {@code etl_failure}. It is guarded by a row in {@code app_migration}:
 * guarding on "is etl_failure empty" would resurrect history an operator deliberately cleaned.</p>
 */
@Component
@Slf4j
public class FailureBackfillService
    implements ApplicationRunner
{
    static final String MIGRATION_NAME = "etl_failure_backfill_v1";

    private static final int BATCH_SIZE = 500;

    private static final String SELECT_SQL = """
        select j.id, j.tid, j.kind, j.start_at, j.duration, j.error_msg, j.execution_id,
               x.biz_date, x.attempt_no,
               case when j.kind in ('COLLECT', 'FILLBACK') then (
                   select a.id from public.addax_log a
                   where j.execution_id is not null and a.execution_id = j.execution_id
                   order by a.id desc limit 1
               ) end as log_id
        from public.etl_jour j
        left join public.etl_execution x on x.id = j.execution_id
        where j.status = false
          and j.error_msg is not null
          and btrim(j.error_msg) <> ''
          and j.start_at >= ?
          and not exists (
              select 1 from public.etl_failure f where f.legacy_jour_id = j.id
          )
          -- A run that already produced a live failure fact (new code) must not be imported again.
          and (j.execution_id is null or not exists (
              select 1 from public.etl_failure f
              where f.execution_id = j.execution_id and f.legacy_jour_id is null
          ))
        order by j.id
        limit ?
        """;

    private static final String COUNT_SQL = """
        select count(*)
        from public.etl_jour j
        where j.status = false
          and j.error_msg is not null
          and btrim(j.error_msg) <> ''
          and j.start_at >= ?
          and not exists (
              select 1 from public.etl_failure f where f.legacy_jour_id = j.id
          )
          and (j.execution_id is null or not exists (
              select 1 from public.etl_failure f
              where f.execution_id = j.execution_id and f.legacy_jour_id is null
          ))
        """;

    private final JdbcTemplate jdbcTemplate;
    private final FailureRecorder failureRecorder;
    private final int retentionDays;

    public FailureBackfillService(JdbcTemplate jdbcTemplate, FailureRecorder failureRecorder,
        @Value("${COLLECTION_FAILURE_RETENTION_DAYS:365}") int retentionDays)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.failureRecorder = failureRecorder;
        this.retentionDays = retentionDays;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        try {
            Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from public.app_migration where name = ?", Integer.class, MIGRATION_NAME);
            if (applied != null && applied > 0) {
                return;
            }
            LocalDate cutoff = LocalDate.now().minusDays(Math.max(retentionDays, 30));
            LocalDateTime from = cutoff.atStartOfDay();
            int total = 0;
            int remaining = countRemaining(from);
            while (remaining > 0) {
                List<LegacyRow> rows = jdbcTemplate.query(SELECT_SQL,
                    (rs, rowNum) -> new LegacyRow(
                        rs.getLong("id"),
                        rs.getLong("tid"),
                        rs.getString("kind"),
                        rs.getTimestamp("start_at") == null ? null : rs.getTimestamp("start_at").toLocalDateTime(),
                        rs.getObject("duration") == null ? null : rs.getInt("duration"),
                        rs.getString("error_msg"),
                        rs.getObject("execution_id") == null ? null : rs.getLong("execution_id"),
                        rs.getDate("biz_date") == null ? null : rs.getDate("biz_date").toLocalDate(),
                        rs.getObject("attempt_no") == null ? null : rs.getInt("attempt_no"),
                        rs.getObject("log_id") == null ? null : rs.getLong("log_id")),
                    from, BATCH_SIZE);
                if (rows.isEmpty()) {
                    break;
                }
                for (LegacyRow row : rows) {
                    recordLegacy(row);
                }
                total += rows.size();
                int after = countRemaining(from);
                // If a row could not be persisted, stop instead of spinning on the same batch.
                if (after >= remaining) {
                    log.error("Legacy failure backfill made no progress with {} row(s) remaining", after);
                    break;
                }
                remaining = after;
            }
            if (remaining == 0) {
                jdbcTemplate.update(
                    "insert into public.app_migration (name) values (?) on conflict (name) do nothing",
                    MIGRATION_NAME);
                log.info("Backfilled {} legacy failure fact(s) from etl_jour since {}", total, cutoff);
            }
            else {
                // Do not mark an incomplete import as done: the next start retries the remainder.
                log.warn("Legacy failure backfill incomplete: {} row(s) remain; migration not marked", remaining);
            }
        }
        catch (Exception e) {
            // A failed backfill must not keep the application from starting; new failures are
            // recorded regardless, and the migration is retried on the next start.
            log.error("Failure fact backfill failed; it will be retried on the next start", e);
        }
    }

    private void recordLegacy(LegacyRow row)
    {
        String summary = FailureText.summarizeAddaxFailure(row.errorMsg(), null);
        String detail = FailureText.extractFailureExcerpt(row.errorMsg(), row.errorMsg());
        FailureEvidence evidence = FailureEvidence.create()
            .stage(FailureStage.fromJourKind(row.kind()))
            .errorClass("LEGACY_JOUR")
            .detail(detail)
            .occurredAt(row.startAt() == null ? null
                : row.startAt().plusSeconds(row.duration() == null ? 0 : row.duration()))
            .attempt(row.attemptNo(), null)
            .finalAttempt(true)
            .runType("FILLBACK".equals(row.kind()) ? "FILLBACK" : "NORMAL")
            .bizDate(row.bizDate())
            .legacyJourId(row.id());
        if (row.executionId() != null) {
            evidence.execution(row.executionId());
        }
        if (row.logId() != null) {
            evidence.log(row.logId(), null);
        }
        failureRecorder.record(row.tid(), summary, evidence);
    }

    private int countRemaining(LocalDateTime from)
    {
        Integer count = jdbcTemplate.queryForObject(COUNT_SQL, Integer.class, from);
        return count == null ? 0 : count;
    }

    private record LegacyRow(
        long id,
        long tid,
        String kind,
        LocalDateTime startAt,
        Integer duration,
        String errorMsg,
        Long executionId,
        LocalDate bizDate,
        Integer attemptNo,
        Long logId
    )
    {
    }
}
