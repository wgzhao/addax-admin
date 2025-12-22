package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlJour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EtlJourRepo
    extends JpaRepository<EtlJour, Long>
{

    void deleteAllByTid(long tableId);

    @Query(value = """
        select error_msg from etl_jour
        where tid = :tableId and status = false
        order by id desc
        limit 1
        """, nativeQuery = true)
    String findLastError(long tableId);

    Optional<EtlJour> findFirstByTidAndStatusIsFalse(long tableId);

    Optional<EtlJour> findFirstByTidAndKindOrderByIdDesc(long tableId, String kind);

    @Query(value = """
        SELECT id FROM etl_jour
        WHERE (:source_tid IS NULL OR tid = :source_tid)
          AND (:before IS NULL OR start_at < :before)
        ORDER BY id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findIdsToDeleteBatch(@org.springframework.data.repository.query.Param("source_tid") Long sourceTid,
        @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before,
        @org.springframework.data.repository.query.Param("limit") int limit);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "delete from etl_jour where id in :ids", nativeQuery = true)
    int deleteByIdInBatch(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

    @Query(value = """
        select count(1) from etl_jour
        where (:source_tid is null or tid = :source_tid)
          and (:before is null or start_at < :before)
        """, nativeQuery = true)
    long countToDelete(@org.springframework.data.repository.query.Param("source_tid") Long sourceTid,
        @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before);
    // 可根据需要添加自定义查询方法
}
