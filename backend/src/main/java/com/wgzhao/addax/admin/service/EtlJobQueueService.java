package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlJobQueueRepo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@AllArgsConstructor
public class EtlJobQueueService
{
    private static final RowMapper<EtlJobQueue> JOB_ROW_MAPPER = (rs, rowNum) -> mapRow(rs);
    private final JdbcTemplate jdbcTemplate;
    private final EtlJobQueueRepo jobRepo;

    private static EtlJobQueue mapRow(ResultSet rs)
        throws SQLException
    {
        EtlJobQueue j = new EtlJobQueue();
        j.setId(rs.getLong("id"));
        j.setTid(rs.getLong("tid"));
        j.setBizDate(rs.getObject("biz_date", LocalDate.class));
        j.setPartName(rs.getString("part_name"));
        j.setPayload(rs.getString("payload"));
        j.setPriority(rs.getInt("priority"));
        j.setStatus(rs.getString("status"));
        j.setAvailableAt(rs.getTimestamp("available_at").toInstant());
        j.setAttempts(rs.getInt("attempts"));
        j.setMaxAttempts(rs.getInt("max_attempts"));
        j.setClaimedBy(rs.getString("claimed_by"));
        var claimedAt = rs.getTimestamp("claimed_at");
        j.setClaimedAt(claimedAt == null ? null : claimedAt.toInstant());
        var leaseUntil = rs.getTimestamp("lease_until");
        j.setLeaseUntil(leaseUntil == null ? null : leaseUntil.toInstant());
        j.setLastError(rs.getString("last_error"));
        j.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        j.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return j;
    }

    private static Instant toInstant(java.sql.Timestamp timestamp)
    {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /**
     * One queue row of a fillback batch, plus the latest execution attempt of that row.
     *
     * <p>The queue row's {@code updated_at} is not refreshed by the terminal update statements, so
     * the execution snapshot is the only reliable source for start/finish times.
     */
    public record FillbackJobRow(
        long id,
        long tid,
        LocalDate bizDate,
        String status,
        String payload,
        int attempts,
        int maxAttempts,
        String lastError,
        Instant createdAt,
        Instant claimedAt,
        Instant execStartedAt,
        Instant execFinishedAt,
        String execError,
        Integer execAttemptNo
    )
    {
    }

    private static final RowMapper<FillbackJobRow> FILLBACK_ROW_MAPPER = (rs, rowNum) -> new FillbackJobRow(
        rs.getLong("id"),
        rs.getLong("tid"),
        rs.getObject("biz_date", LocalDate.class),
        rs.getString("status"),
        rs.getString("payload"),
        rs.getInt("attempts"),
        rs.getInt("max_attempts"),
        rs.getString("last_error"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("claimed_at")),
        toInstant(rs.getTimestamp("exec_started_at")),
        toInstant(rs.getTimestamp("exec_finished_at")),
        rs.getString("exec_error"),
        rs.getObject("exec_attempt_no", Integer.class));

    public long countPending()
    {
        return jobRepo.countByStatus("pending");
    }

    @Transactional
    public int enqueue(EtlTable table, LocalDate bizDate, int priority)
    {
        return enqueue(table, bizDate, priority, null);
    }

    @Transactional
    public int enqueue(EtlTable table, LocalDate bizDate, int priority, String payload)
    {
        int maxAttempts = table.getRetryCnt() == null ? 3 : table.getRetryCnt();
        // Concurrency-safe dedup: the partial unique index uq_etl_job_queue_active_tid_biz_date
        // backs the ON CONFLICT target, so two concurrent enqueues of the same (tid, biz_date)
        // cannot both insert even under READ COMMITTED (the old WHERE NOT EXISTS could).
        String sql = """
                INSERT INTO public.etl_job_queue (tid, biz_date, part_name, priority, status, available_at, attempts, max_attempts, payload)
                SELECT ?, ?, ?, ?, 'pending', now(), 0, ?, ?::jsonb
                ON CONFLICT (tid, biz_date) WHERE status IN ('pending','running') DO NOTHING
            """;
        return jdbcTemplate.update(sql,
            table.getId(), bizDate, table.getPartName(), priority, maxAttempts, payload);
    }

    @Transactional
    public Optional<EtlJobQueue> claimNext(String instanceId, int leaseSeconds)
    {
        String sql = """
            WITH cte AS (
                SELECT id
                FROM public.etl_job_queue
                WHERE status='pending' AND available_at <= now()
                ORDER BY priority, available_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE public.etl_job_queue q
            SET status='running', claimed_by=?, claimed_at=now(), lease_until=now() + ?::interval, attempts=attempts+1
            FROM cte
            WHERE q.id = cte.id
            RETURNING q.*
            """;
        String leaseInterval = leaseSeconds + " seconds";
        List<EtlJobQueue> list = jdbcTemplate.query(sql, JOB_ROW_MAPPER, instanceId, leaseInterval);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /**
     * Read-only peek at pending jobs without changing queue state.
     */
    @Transactional(readOnly = true)
    public List<EtlJobQueue> peekPendingJobs(int limit)
    {
        String sql = """
            SELECT *
            FROM public.etl_job_queue
            WHERE status='pending' AND available_at <= now()
            ORDER BY priority, available_at, id
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, JOB_ROW_MAPPER, Math.max(1, limit));
    }

    /**
     * Master-mode: atomically claim a specific pending job and assign it to a worker instance.
     */
    @Transactional
    public Optional<EtlJobQueue> assignSpecificJobToWorker(long jobId, String targetInstanceId, int leaseSeconds)
    {
        String sql = """
            WITH cte AS (
                SELECT id
                FROM public.etl_job_queue
                WHERE id=? AND status='pending' AND available_at <= now()
                FOR UPDATE SKIP LOCKED
            )
            UPDATE public.etl_job_queue q
            SET status='running', claimed_by=?, claimed_at=now(), lease_until=now() + ?::interval, attempts=attempts+1
            FROM cte
            WHERE q.id = cte.id
            RETURNING q.*
            """;
        String leaseInterval = leaseSeconds + " seconds";
        List<EtlJobQueue> list = jdbcTemplate.query(sql, JOB_ROW_MAPPER, jobId, targetInstanceId, leaseInterval);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Transactional
    public void completeSuccess(long jobId, String instanceId)
    {
        String sql = "UPDATE public.etl_job_queue SET status='completed', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=NULL WHERE id=? AND status='running' AND claimed_by=?";
        jdbcTemplate.update(sql, jobId, instanceId);
    }

    @Transactional
    public void releaseClaim(long jobId, int delaySeconds, String instanceId, int stillProvisionalSeconds)
    {
        // Only compensate a claim this master still owns. The lease check distinguishes an
        // undelivered claim (short provisional lease) from one the worker already confirmed (full
        // lease): a publish may time out after the message was delivered, and stripping ownership
        // from a run that is about to start is how a duplicate execution begins. The attempt is
        // rolled back because an undelivered claim never executed; otherwise master failover and
        // dead-heartbeat assignments would silently consume the table's retry budget.
        String sql = """
                UPDATE public.etl_job_queue
                SET status='pending', claimed_by=NULL, claimed_at=NULL, lease_until=NULL,
                    available_at=now() + ?::interval,
                    attempts = case when attempts > 0 then attempts - 1 else 0 end
                WHERE id=? AND status='running' AND claimed_by=?
                  AND lease_until < now() + ?::interval
            """;
        String delayInterval = delaySeconds + " seconds";
        String provisionalInterval = stillProvisionalSeconds + " seconds";
        jdbcTemplate.update(sql, delayInterval, jobId, instanceId, provisionalInterval);
    }

    @Transactional
    public void completeFailure(long jobId, String error)
    {
        String sql = "UPDATE public.etl_job_queue SET status='failed', last_error=? WHERE id=?";
        jdbcTemplate.update(sql, truncateError(error), jobId);
    }

    @Transactional
    public void failOrReschedule(EtlJobQueue job, String error, Duration backoff, String instanceId)
    {
        // Both branches are guarded by claimed_by so an executor that lost ownership (dead-worker
        // recovery, lease expiry) can never mutate a row a successor worker is executing.
        boolean canRetry = job.getAttempts() < job.getMaxAttempts();
        if (canRetry) {
            String sql = """
                    UPDATE public.etl_job_queue
                    SET status='pending', available_at = now() + ?::interval,
                        lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                    WHERE id=? AND status='running' AND claimed_by=?
                """;
            String interval = backoff.getSeconds() + " seconds";
            jdbcTemplate.update(sql, interval, truncateError(error), job.getId(), instanceId);
        }
        else {
            String sql = """
                    UPDATE public.etl_job_queue
                    SET status='failed', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                    WHERE id=? AND status='running' AND claimed_by=?
                """;
            jdbcTemplate.update(sql, truncateError(error), job.getId(), instanceId);
        }
    }

    @Transactional
    public void completeCancelled(long jobId, String reason, String instanceId)
    {
        String sql = """
                UPDATE public.etl_job_queue
                SET status='cancelled', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                WHERE id=? AND status='running' AND claimed_by=?
            """;
        jdbcTemplate.update(sql, truncateError(reason), jobId, instanceId);
    }

    private String truncateError(String error)
    {
        if (error == null) {
            return null;
        }
        return error.length() > 4000 ? error.substring(0, 4000) : error;
    }

    @Transactional
    public int recoverExpiredLeases()
    {
        String sql = """
                UPDATE public.etl_job_queue
                SET status='pending', available_at=now(), lease_until=NULL, claimed_by=NULL, claimed_at=NULL
                WHERE status='running' AND lease_until < now()
            """;
        return jdbcTemplate.update(sql);
    }

    @Transactional
    public int clearPending()
    {
        String sql = "DELETE FROM public.etl_job_queue WHERE status='pending'";
        return jdbcTemplate.update(sql);
    }

    /**
     * Master-mode: atomically claim the next pending job and assign it to a specific worker instance.
     * Semantically identical to claimNext() but claimed_by is the target worker, not the local node.
     */
    @Transactional
    public Optional<EtlJobQueue> assignToWorker(String targetInstanceId, int leaseSeconds)
    {
        String sql = """
            WITH cte AS (
                SELECT id
                FROM public.etl_job_queue
                WHERE status='pending' AND available_at <= now()
                ORDER BY priority, available_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE public.etl_job_queue q
            SET status='running', claimed_by=?, claimed_at=now(), lease_until=now() + ?::interval, attempts=attempts+1
            FROM cte
            WHERE q.id = cte.id
            RETURNING q.*
            """;
        String leaseInterval = leaseSeconds + " seconds";
        List<EtlJobQueue> list = jdbcTemplate.query(sql, JOB_ROW_MAPPER, targetInstanceId, leaseInterval);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    // Renew lease for a running, claimed job. Returns true if the lease was renewed.
    @Transactional
    public boolean renewLease(long jobId, String instanceId, int leaseSeconds)
    {
        String sql = "UPDATE public.etl_job_queue SET lease_until = now() + ?::interval WHERE id = ? AND claimed_by = ? AND status = 'running'";
        String interval = leaseSeconds + " seconds";
        int updated = jdbcTemplate.update(sql, interval, jobId, instanceId);
        return updated > 0;
    }

    @Transactional
    public void truncateQueueExceptRunningTasksBefore(Instant createdBefore)
    {
        // Daily-refresh cleanup with a created_at cutoff: terminal history plus auto-scheduled
        // pending rows from the previous period are dropped, while rows enqueued during the refresh
        // window (created_at >= cutoff, e.g. by other nodes whose monitors kept running) and manual
        // submits / fillbacks (pending rows carrying a payload) are preserved.
        String sql = """
                DELETE FROM public.etl_job_queue
                WHERE status <> 'running'
                  AND created_at < ?
                  AND NOT (status = 'pending' AND payload IS NOT NULL)
            """;
        jdbcTemplate.update(sql, java.sql.Timestamp.from(createdBefore));
    }

    /**
     * Queue rows of one fillback batch, joined with the latest execution attempt of each row.
     *
     * @param tid     table id
     * @param batchId batch id stored in the row payload
     * @return rows ordered by business date
     */
    public List<FillbackJobRow> findFillbackBatchRows(long tid, String batchId)
    {
        String sql = """
            SELECT q.id, q.tid, q.biz_date, q.status, q.payload, q.attempts, q.max_attempts,
                   q.last_error, q.created_at, q.claimed_at,
                   e.started_at AS exec_started_at, e.finished_at AS exec_finished_at,
                   e.error_message AS exec_error, e.attempt_no AS exec_attempt_no
            FROM public.etl_job_queue q
            LEFT JOIN LATERAL (
                SELECT started_at, finished_at, error_message, attempt_no
                FROM public.etl_execution
                WHERE job_queue_id = q.id
                ORDER BY id DESC
                LIMIT 1
            ) e ON true
            WHERE q.tid = ? AND q.payload->>'batch_id' = ?
            ORDER BY q.biz_date, q.id
            """;
        return jdbcTemplate.query(sql, FILLBACK_ROW_MAPPER, tid, batchId);
    }

    /**
     * Latest fillback batch id for a table, used when the detail page reloads without a batch id.
     *
     * @param tid table id
     * @return the newest batch id, or empty when the table has no surviving fillback rows
     */
    public Optional<String> findLatestFillbackBatchId(long tid)
    {
        String sql = """
            SELECT payload->>'batch_id'
            FROM public.etl_job_queue
            WHERE tid = ?
              AND payload->>'action' = 'fillback'
              AND payload->>'batch_id' IS NOT NULL
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """;
        List<String> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), tid);
        return ids.isEmpty() ? Optional.empty() : Optional.ofNullable(ids.getFirst());
    }

    /**
     * Release all running tasks claimed by a specific worker instance.
     * Called when master detects a worker has disappeared between dispatch cycles.
     */
    public List<EtlJobQueue> findActiveByTid(long tid)
    {
        String sql = """
                SELECT *
                FROM public.etl_job_queue
                WHERE tid = ? AND status IN ('pending','running')
                ORDER BY id
            """;
        return jdbcTemplate.query(sql, JOB_ROW_MAPPER, tid);
    }

    @Transactional
    public int cancelPendingByTid(long tid)
    {
        String sql = "UPDATE public.etl_job_queue SET status='cancelled', last_error=? WHERE tid=? AND status='pending'";
        return jdbcTemplate.update(sql, "Cancelled by user request", tid);
    }

}
