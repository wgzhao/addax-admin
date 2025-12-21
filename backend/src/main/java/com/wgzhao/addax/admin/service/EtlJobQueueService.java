package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlJobQueueRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class EtlJobQueueService
{

    private static final RowMapper<EtlJobQueue> JOB_ROW_MAPPER = (rs, rowNum) -> mapRow(rs);
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EtlJobQueueRepo jobRepo;

    private static EtlJobQueue mapRow(ResultSet rs)
        throws SQLException
    {
        EtlJobQueue j = new EtlJobQueue();
        j.setId(rs.getLong("id"));
        j.setTid(rs.getLong("tid"));
        j.setBizDate(rs.getObject("biz_date", LocalDate.class));
        j.setPartName(rs.getString("part_name"));
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

    public long countActive()
    {
        return jobRepo.countActive();
    }

    public long countPending()
    {
        return jobRepo.countPending();
    }

    public long countRunning()
    {
        return jobRepo.countRunning();
    }

    public long countFailed()
    {
        return jobRepo.countFailed();
    }

    public long countCompleted()
    {
        return jobRepo.countCompleted();
    }

    @Transactional
    public int enqueue(EtlTable table, LocalDate bizDate, int priority)
    {
        int maxAttempts = table.getRetryCnt() == null ? 3 : table.getRetryCnt();
        String sql = """
                INSERT INTO public.etl_job_queue (tid, biz_date, part_name, priority, status, available_at, attempts, max_attempts)
                SELECT ?, ?, ?, ?, 'pending', now(), 0, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM public.etl_job_queue
                    WHERE tid = ? AND biz_date = ? AND status IN ('pending','running')
                )
            """;
        return jdbcTemplate.update(sql,
            table.getId(), bizDate, table.getPartName(), priority, maxAttempts,
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
                ORDER BY priority ASC, available_at ASC
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
        String sql = "UPDATE public.etl_job_queue SET status='completed', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=NULL WHERE id=?";
        jdbcTemplate.update(sql, jobId);
    }

    @Transactional
    public void releaseClaim(long jobId)
    {
        // 兼容老的调用，使用默认 5s 的不可见期
        releaseClaim(jobId, 5);
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
                    WHERE id=?
                """;
            String interval = backoff.getSeconds() + " seconds";
            jdbcTemplate.update(sql, interval, truncateError(error), job.getId());
        }
        else {
            String sql = """
                    UPDATE public.etl_job_queue
                    SET status='failed', lease_until=NULL, claimed_by=NULL, claimed_at=NULL, last_error=?
                    WHERE id=?
                """;
            jdbcTemplate.update(sql, truncateError(error), job.getId());
        }
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
}
