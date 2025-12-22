package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AddaxLogRepo
    extends JpaRepository<AddaxLog, Long>
{
    AddaxLog findFirstByTidOrderByRunDateDesc(Long tid);

    List<AddaxLog> findTop5ByTidOrderByRunDateDesc(Long tid);

    List<AddaxLog> findTop5ByTidAndRunDateGreaterThanOrderByIdDesc(Long tid, LocalDate runDate);

    AddaxLog findByTidAndRunDate(Long tid, LocalDate runDate);

    @Query(value = """
        select new com.wgzhao.addax.admin.dto.AddaxLogDto(a.id, to_char(a.runAt, 'YYYY-MM-DD HH24:MI:SS'))
        from AddaxLog a
        where a.tid = ?1
        order by a.runAt desc
        limit 5
        """)
    List<AddaxLogDto> findLogEntry(String tid);

    @Query(value = "select log from addax_log where id = ?1", nativeQuery = true)
    String findLogById(Long id);

    @Query(value = """
        select id from addax_log
        where (:source is null or tid = :source_tid) and (:before is null or run_at < :before)
        order by id
        limit :limit
        """, nativeQuery = true)
    List<Long> findIdsToDeleteBatch(@Param("source_tid") Long sourceTid,
        @Param("before") LocalDateTime before,
        @Param("limit") int limit);

    @Modifying
    @Query(value = "delete from addax_log where id in :ids", nativeQuery = true)
    int deleteByIdInBatch(@Param("ids") List<Long> ids);

    @Query(value = """
        select count(1) from addax_log
        where (:source_tid is null or tid = :source_tid)
          and (:before is null or run_at < :before)
        """, nativeQuery = true)
    long countToDelete(@Param("source_tid") Long sourceTid,
        @Param("before") LocalDateTime before);

    // 按 id 倒序分页查询 AddaxLog 实体
    Page<AddaxLog> findAllByOrderByIdDesc(Pageable pageable);

    @Modifying
    @Query("delete from AddaxLog a where a.runDate < :before")
    void deleteByRunAtBefore(@Param("before") LocalDate before);
}
