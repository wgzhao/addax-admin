package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJobQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface EtlJobQueueRepo
    extends JpaRepository<EtlJobQueue, Long>
{
    long countByStatus(String pending);

    /**
     * Automatic work (no payload snapshot) still owed for one business date.
     * Manual submits and fillbacks carry a payload; they must not keep a day's
     * batch "unfinished" forever.
     */
    @Query("""
            SELECT count(q) FROM EtlJobQueue q
            WHERE q.bizDate = :bizDate AND q.status IN ('pending', 'running') AND q.payload IS NULL
        """)
    long countActiveAutomatic(@Param("bizDate") LocalDate bizDate);
}

