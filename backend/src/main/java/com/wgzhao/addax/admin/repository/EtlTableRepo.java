package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;

public interface EtlTableRepo
    extends JpaRepository<EtlTable, Long>
{
    @Query(value = """
        select count(t.id) from VwEtlTableWithSource t
        where t.status not in ( 'X' ,'U')
              and t.enabled = true
        """)
    Integer findValidTableCount();

    @Modifying
    @Query("UPDATE EtlTable t SET t.status = 'N', t.retryCnt = 3 where t.status not in ( 'X', 'U')")
    void resetAllEtlFlags();

    @Query(value = """
        select t
        from EtlTable t
        where (t.status='E' or t.duration>=1200 or t.retryCnt<3) and t.status <> 'X'
        order by t.duration desc
        """)
    List<EtlTable> findSpecialTasks();

    @Modifying
    @Query(value = """
                update EtlTable t
                set t.status = ?2, t.retryCnt = ?3
                where t.id in ?1
        """)
    void batchUpdateStatusAndFlag(List<Long> ids, String status, int retryCnt);

    @Query(value = """
        select count(*) from etl_table t left join etl_source s on t.sid = s.id
        where t.status = ?1 and s.enabled = true
        """, nativeQuery = true)
    int countByStatusEquals(String status);

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
            WHERE t.status NOT IN ('Y','X','U') AND t.retryCnt > 0
            AND (
                :checkTime = false OR
                (s.startAt > :switchTime AND s.startAt < :currentTime)
            )
        """)
    List<EtlTable> findRunnableTasks(@Param("switchTime") LocalTime switchTime,
        @Param("currentTime") LocalTime currentTime,
        @Param("checkTime") boolean checkTime);

    /**
     * 查询某个 source 下“继承调度”的可运行任务（表 startAt 为空，实际调度由 source.startAt 决定）
     */
    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
            WHERE s.enabled = true
              AND t.sid = :sid
              AND t.startAt is null
              AND t.status NOT IN ('Y','X','U')
              AND t.retryCnt > 0
        """)
    List<EtlTable> findRunnableInheritedTasksBySource(@Param("sid") int sid);

    /**
     * 查询“表级覆盖调度”的可运行任务（表 startAt 等于指定时间点）
     */
    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
            WHERE s.enabled = true
              AND t.startAt = :startAt
              AND t.status = 'N'
              AND t.retryCnt > 0
        """)
    List<EtlTable> findRunnableOverrideTasksByStartAt(@Param("startAt") LocalTime startAt);

    /**
     * 查询“表级覆盖调度”的可运行任务（表 startAt 落在指定时间窗口内，闭区间）。
     */
    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
            WHERE s.enabled = true
              AND t.startAt is not null
              AND t.startAt >= :from
              AND t.startAt <= :to
              AND t.status = 'N'
              AND t.retryCnt > 0
        """)
    List<EtlTable> findRunnableOverrideTasksBetween(@Param("from") LocalTime from,
        @Param("to") LocalTime to);

    int countBySid(int sid);

    List<EtlTable> findByStatus(String status);

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
                WHERE t.status <> 'X' AND s.enabled = true
        """)
    List<EtlTable> findCanRefreshTables();
}
