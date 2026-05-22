package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlJobQueueRepo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
        return j;
    }

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
        String sql = """
                INSERT INTO public.etl_job_queue (tid, biz_date, part_name, priority, status, available_at, attempts, max_attempts, payload)
                SELECT ?, ?, ?, ?, 'pending', now(), 0, ?, ?::jsonb
                WHERE NOT EXISTS (
                    SELECT 1 FROM public.etl_job_queue
                    WHERE tid = ? AND biz_date = ? AND status IN ('pending','running')
                )
            """;
        return jdbcTemplate.update(sql,
            table.getId(), bizDate, table.getPartName(), priority, maxAttempts, payload,
            table.getId(), bizDate);
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

    @Transactional
    public void completeSuccess(long jobId)
    {
        String sql = "UPDATE public.etl_job_queue SET status='completed', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=NULL WHERE id=? AND status='running'";
        jdbcTemplate.update(sql, jobId);
    }

    @Transactional
    public void releaseClaim(long jobId, int delaySeconds)
    {
        String sql = "UPDATE public.etl_job_queue SET status='pending', claimed_by=NULL, claimed_at=NULL, lease_until=NULL, available_at=now() + ?::interval WHERE id=?";
        String interval = delaySeconds + " seconds";
        jdbcTemplate.update(sql, interval, jobId);
    }

    @Transactional
    public void completeFailure(long jobId, String error)
    {
        String sql = "UPDATE public.etl_job_queue SET status='failed', last_error=? WHERE id=?";
        jdbcTemplate.update(sql, truncateError(error), jobId);
    }

    @Transactional
    public void failOrReschedule(EtlJobQueue job, String error, Duration backoff)
    {
        boolean canRetry = job.getAttempts() < job.getMaxAttempts();
        if (canRetry) {
            String sql = """
                    UPDATE public.etl_job_queue
                    SET status='pending', available_at = now() + ?::interval,
                        lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                    WHERE id=? AND status='running'
                """;
            String interval = backoff.getSeconds() + " seconds";
            jdbcTemplate.update(sql, interval, truncateError(error), job.getId());
        }
        else {
            String sql = """
                    UPDATE public.etl_job_queue
                    SET status='failed', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                    WHERE id=? AND status='running'
                """;
            jdbcTemplate.update(sql, truncateError(error), job.getId());
        }
    }

    @Transactional
    public void completeCancelled(long jobId, String reason)
    {
        String sql = """
                UPDATE public.etl_job_queue
                SET status='cancelled', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                WHERE id=? AND status='running'
            """;
        jdbcTemplate.update(sql, truncateError(reason), jobId);
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
    public void truncateQueueExceptRunningTasks()
    {
        jobRepo.deleteByStatusNot("running");
    }

    /**
     * Release all running tasks claimed by a specific worker instance.
     * Called when master detects a worker has disappeared between dispatch cycles.
     */
    @Transactional
    public int releaseClaimedByInstance(String instanceId)
    {
        String sql = """
                UPDATE public.etl_job_queue
                SET status='pending', available_at=now(), lease_until=NULL, claimed_by=NULL, claimed_at=NULL
                WHERE status='running' AND claimed_by=?
            """;
        return jdbcTemplate.update(sql, instanceId);
    }

    /**
     * Release running tasks whose claimed_by is not in the provided alive worker set.
     * Called once on master startup (after waiting for workers to re-register) to recover
     * tasks that were claimed by nodes that never came back.
     * If aliveInstanceIds is empty, ALL running tasks are released (entire cluster was restarted).
     */
    @Transactional
    public int releaseOrphanedJobs(Set<String> aliveInstanceIds)
    {
        if (aliveInstanceIds.isEmpty()) {
            String sql = """
                    UPDATE public.etl_job_queue
                    SET status='pending', available_at=now(), lease_until=NULL, claimed_by=NULL, claimed_at=NULL
                    WHERE status='running'
                """;
            return jdbcTemplate.update(sql);
        }
        String placeholders = aliveInstanceIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "UPDATE public.etl_job_queue SET status='pending', available_at=now(), lease_until=NULL, claimed_by=NULL, claimed_at=NULL WHERE status='running' AND claimed_by NOT IN (" + placeholders + ")";
        return jdbcTemplate.update(sql, aliveInstanceIds.toArray());
    }
}
