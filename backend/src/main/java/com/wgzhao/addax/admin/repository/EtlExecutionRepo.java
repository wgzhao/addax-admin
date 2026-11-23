package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlExecution;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EtlExecutionRepo
    extends JpaRepository<EtlExecution, Long>
{
    Page<EtlExecution> findByTidOrderByStartedAtDescIdDesc(long tid, Pageable pageable);

    List<EtlExecution> findTop15ByTidOrderByStartedAtDescIdDesc(long tid);

    Optional<EtlExecution> findTop1ByTidOrderByStartedAtDescIdDesc(long tid);

    /**
     * Used to tell "this table never failed" apart from "its failure facts were purged": if a failed
     * execution still exists but no failure fact does, the facts must have been cleaned.
     */
    boolean existsByTidAndStatus(long tid, String status);

    /**
     * Close executions whose queue ownership is gone. An execution outlives its attempt only when
     * the owner could not write a terminal state: the queue row was requeued/terminalized, or it
     * still claims the row under an expired lease. Unlinked executions (manual synchronous runs,
     * job_queue_id is null) are left alone because their owner cannot be inferred from the queue.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE etl_execution e
           SET status = 'failed',
               finished_at = now(),
               error_message = :reason
         WHERE e.status = 'running'
           AND e.job_queue_id IS NOT NULL
           AND NOT EXISTS (
               SELECT 1
                 FROM etl_job_queue q
                WHERE q.id = e.job_queue_id
                  AND q.status = 'running'
                  AND q.lease_until > now()
           )
        """, nativeQuery = true)
    int failOrphanedRunningExecutions(@Param("reason") String reason);
}
