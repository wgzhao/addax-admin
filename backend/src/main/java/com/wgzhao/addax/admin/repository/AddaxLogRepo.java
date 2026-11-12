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
import java.util.Collection;
import java.util.List;

public interface AddaxLogRepo
    extends JpaRepository<AddaxLog, Long>
{
    @Query(value = """
        select new com.wgzhao.addax.admin.dto.AddaxLogDto(
            a.id, a.executionId, to_char(a.runAt, 'YYYY-MM-DD HH24:MI:SS'))
        from AddaxLog a
        where a.tid = ?1
        order by a.runAt desc
        limit 5
        """)
    List<AddaxLogDto> findLogEntry(String tid);

    @Query(value = "select log from addax_log where id = ?1", nativeQuery = true)
    String findLogById(Long id);

    @Query("select a.tid from AddaxLog a where a.id = :id")
    Long findTidById(@Param("id") Long id);

    // 按 id 倒序分页查询 AddaxLog 实体
    Page<AddaxLog> findAllByOrderByIdDesc(Pageable pageable);

    @Query("""
        select a from AddaxLog a
        where a.tid in (select t.id from EtlTable t where t.sid in :sourceIds)
        order by a.id desc
        """)
    Page<AddaxLog> findBySourceIds(@Param("sourceIds") Collection<Integer> sourceIds, Pageable pageable);

    @Modifying
    @Query("delete from AddaxLog a where a.runDate < :before")
    void deleteByRunAtBefore(@Param("before") LocalDate before);
}
