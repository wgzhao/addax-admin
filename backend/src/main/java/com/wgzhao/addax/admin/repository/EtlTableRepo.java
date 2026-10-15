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

    /**
     * Mark a table as collected. Guarded so a stale executor (duplicate run that lost ownership)
     * cannot downgrade a fresher terminal state, and a row deleted mid-run is never re-inserted
     * through a whole-row merge. duration is written here because this terminal update replaced the
     * former whole-row save; without it the last-run elapsed time stayed frozen at its old value.
     */
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status='Y', t.retryCnt=3, t.endTime=:endTime, t.duration=:duration
            WHERE t.id=:id AND t.status NOT IN ('Y','X')
        """)
    int markFinished(@Param("id") long id, @Param("endTime") java.util.Date endTime,
        @Param("duration") long duration);

    /**
     * Mark a table as failed, decrementing retryCnt against the row's current value. A success
     * written by another (duplicate) executor is never downgraded to failed. duration is written
     * here for the same reason as {@link #markFinished}.
     */
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status='E', t.endTime=:endTime,
                  t.retryCnt = case when t.retryCnt > 0 then t.retryCnt - 1 else 0 end,
                  t.duration=:duration
            WHERE t.id=:id AND t.status NOT IN ('Y','X')
        """)
    int markFailed(@Param("id") long id, @Param("endTime") java.util.Date endTime,
        @Param("duration") long duration);

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

    /**
     * Collection calendar of the source a table belongs to, for callers that only have a table id.
     * Null (or blank) means natural day.
     */
    @Query(value = """
        select s.calendar from etl_table t join etl_source s on t.sid = s.id where t.id = ?1
        """, nativeQuery = true)
    String findSourceCalendarByTid(long tid);

    /**
     * Distinct calendars in use by tables that can still collect, so the missing-collection report can
     * judge each group of tables against its own calendar.
     */
    @Query(value = """
        select distinct coalesce(nullif(calendar, ''), 'NATURAL_DAY')
        from vw_etl_table_with_source
        where status <> 'X' and coalesce(enabled, false) = true
        """, nativeQuery = true)
    List<String> findCalendarsInUse();

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
                WHERE t.status <> 'X' AND s.enabled = true
        """)
    List<EtlTable> findCanRefreshTables();
}
