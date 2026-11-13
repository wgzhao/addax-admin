package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EtlFailureRepo
    extends JpaRepository<EtlFailure, Long>, JpaSpecificationExecutor<EtlFailure>
{
    Optional<EtlFailure> findFirstByTidOrderByOccurredAtDescIdDesc(long tid);

    Optional<EtlFailure> findFirstByTidAndStageOrderByOccurredAtDescIdDesc(long tid, String stage);

    boolean existsByExecutionIdAndStageAndFingerprint(Long executionId, String stage, String fingerprint);

    boolean existsByLegacyJourId(Long legacyJourId);

    @Query(value = """
        select distinct on (f.tid) f.*
        from public.etl_failure f
        where f.tid in (:tids)
        order by f.tid, f.occurred_at desc, f.id desc
        """, nativeQuery = true)
    List<EtlFailure> findLatestForTids(@Param("tids") Collection<Long> tids);

    @Query("""
        select count(f), min(f.occurredAt), max(f.occurredAt)
        from EtlFailure f
        where f.tid = :tid and f.fingerprint = :fingerprint
        """)
    List<Object[]> summarizeOccurrences(@Param("tid") long tid, @Param("fingerprint") String fingerprint);
}
