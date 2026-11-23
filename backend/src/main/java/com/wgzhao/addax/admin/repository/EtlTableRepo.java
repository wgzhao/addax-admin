package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

public interface EtlTableRepo
    extends JpaRepository<EtlTable, Long>
{
    List<EtlTable> findBySid(int sid);

    long countByTargetId(Long targetId);

    @Query(value = """
        select count(t.id) from VwEtlTableWithSource t
        where t.status not in ( 'X' ,'U')
              and t.enabled = true
              and t.retiredAt is null
        """)
    Integer findValidTableCount();

    /**
     * Mark the tables a business day still owes a collection: those whose source calendar covers the day
     * now open. Callers pass the calendars that are due, so a work-day / trade-day source on a rest day
     * keeps the status of its last collection — the calendar gate refuses its trigger, so nothing could
     * ever move such a table back to 'Y' until the next business day.
     */
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status = 'N', t.retryCnt = 3
            WHERE t.status not in ('X', 'U')
              AND t.retiredAt is null
              AND COALESCE(NULLIF((SELECT s.calendar FROM EtlSource s WHERE s.id = t.sid), ''), 'NATURAL_DAY')
                  in (:calendars)
        """)
    int resetEtlFlagsForCalendars(@Param("calendars") List<String> calendars);

    @Modifying
    @Query(value = """
                update EtlTable t
                set t.status = ?2, t.retryCnt = ?3
                where t.id in ?1
                  and t.retiredAt is null
        """)
    void batchUpdateStatusAndFlag(List<Long> ids, String status, int retryCnt);

    /**
     * Mark a table as running. A paused ('X') table keeps its status: manual runs and fillbacks are
     * allowed on paused tables, and flipping X to R here would make the terminal update write Y and
     * silently resume automatic scheduling on the next switch.
     */
    // Called from the executor outside any service transaction, so the repository method opens its own.
    @Transactional
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status = CASE WHEN t.status = 'X' THEN 'X' ELSE 'R' END,
                  t.startTime=:startTime
            WHERE t.id=:id
        """)
    int markRunning(@Param("id") long id, @Param("startTime") java.util.Date startTime);

    /**
     * Mark a table as waiting for a schema refresh. A paused ('X') table keeps its status for the same
     * reason as {@link #markRunning}: schema refresh never visits paused tables, so 'U' would strand it.
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status = CASE WHEN t.status = 'X' THEN 'X' ELSE 'U' END
            WHERE t.id=:id
        """)
    int markUpdateSchema(@Param("id") long id);

    /**
     * Mark a table as collected. Guarded so a stale executor (duplicate run that lost ownership)
     * cannot downgrade a fresher terminal state, and a row deleted mid-run is never re-inserted
     * through a whole-row merge. duration is written here because this terminal update replaced the
     * former whole-row save; without it the last-run elapsed time stayed frozen at its old value.
     * A paused table keeps 'X' but still records when the manual run finished.
     */
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status = CASE WHEN t.status = 'X' THEN 'X' ELSE 'Y' END,
                  t.retryCnt=3, t.endTime=:endTime, t.duration=:duration
            WHERE t.id=:id AND t.status <> 'Y'
        """)
    int markFinished(@Param("id") long id, @Param("endTime") java.util.Date endTime,
        @Param("duration") long duration);

    /**
     * Mark a table as failed, decrementing retryCnt against the row's current value. A success
     * written by another (duplicate) executor is never downgraded to failed. duration is written
     * here for the same reason as {@link #markFinished}. A paused table keeps 'X' and its retry
     * budget: the budget only governs automatic scheduling, which a paused table does not take part in.
     */
    // The daily refresh calls this outside any service transaction, so the repository method must
    // open its own; otherwise Hibernate rejects the bulk update with TransactionRequiredException.
    @Transactional
    @Modifying
    @Query("""
            UPDATE EtlTable t SET t.status = CASE WHEN t.status = 'X' THEN 'X' ELSE 'E' END,
                  t.endTime=:endTime,
                  t.retryCnt = case when t.status = 'X' then t.retryCnt
                                    when t.retryCnt > 0 then t.retryCnt - 1 else 0 end,
                  t.duration=:duration
            WHERE t.id=:id AND t.status <> 'Y'
        """)
    int markFailed(@Param("id") long id, @Param("endTime") java.util.Date endTime,
        @Param("duration") long duration);

    @Query(value = """
        select count(*) from etl_table t left join etl_source s on t.sid = s.id
        where t.status = ?1 and s.enabled = true and t.retired_at is null
        """, nativeQuery = true)
    int countByStatusEquals(String status);

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
            WHERE t.retiredAt is null
              AND t.status NOT IN ('Y','X','U') AND t.retryCnt > 0
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
              AND t.retiredAt is null
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
              AND t.retiredAt is null
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
              AND t.retiredAt is null
              AND t.startAt is not null
              AND t.startAt >= :from
              AND t.startAt <= :to
              AND t.status = 'N'
              AND t.retryCnt > 0
        """)
    List<EtlTable> findRunnableOverrideTasksBetween(@Param("from") LocalTime from,
        @Param("to") LocalTime to);

    int countBySid(int sid);

    List<EtlTable> findByStatusAndRetiredAtIsNull(String status);

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
        where status <> 'X' and coalesce(enabled, false) = true and retired_at is null
        """, nativeQuery = true)
    List<String> findCalendarsInUse();

    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s on t.sid = s.id
                WHERE t.status <> 'X' AND s.enabled = true AND t.retiredAt is null
        """)
    List<EtlTable> findCanRefreshTables();

    /**
     * Tables a business date still owes work for: enabled source, not paused, not
     * retired, and the source calendar covers that date. Used to decide whether a
     * day's automatic batch is finished.
     */
    @Query("""
            SELECT t FROM EtlTable t JOIN EtlSource s ON t.sid = s.id
            WHERE t.retiredAt IS NULL AND s.enabled = true AND t.status <> 'X'
              AND COALESCE(NULLIF(s.calendar, ''), 'NATURAL_DAY') IN (:calendars)
        """)
    List<EtlTable> findDueCandidates(@Param("calendars") List<String> calendars);

    /**
     * Target databases a source has used on one target endpoint, most recently touched first. The
     * batch wizard uses this only as a suggestion; the target database remains an explicit route
     * field and is never derived from the source.
     */
    @Query(value = """
        select t.target_db
        from etl_table t
        where t.sid = :sid
          and t.target_id = :targetId
          and t.retired_at is null
          and t.target_db is not null
          and btrim(t.target_db) <> ''
        group by t.target_db
        order by max(t.updated_at) desc nulls last, count(*) desc, t.target_db
        """, nativeQuery = true)
    List<String> findRecentTargetDbs(@Param("sid") int sid, @Param("targetId") long targetId);
}
