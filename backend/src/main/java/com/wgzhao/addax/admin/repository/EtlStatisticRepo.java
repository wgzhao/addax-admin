package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EtlStatisticRepo
    extends JpaRepository<EtlStatistic, Long>
{

    Optional<EtlStatistic> findByTidAndBizDate(long tid, LocalDate bizDate);

    @Query(value = """
        SELECT s1 FROM EtlStatistic s1
        WHERE s1.totalErrors > 0
        AND s1.bizDate = (
            SELECT MAX(s2.bizDate)
            FROM EtlStatistic s2
            WHERE s2.tid = s1.tid
        )
        """)
    List<EtlStatistic> findErrorTask();

    @Query(value = """
        select
            biz_date,
            array_agg(code) AS sources,
            array_agg(total_secs) AS total_secs
        FROM (
                 SELECT
                     b.code,
                     t.biz_date,
                     SUM(t.take_secs) AS total_secs
                 FROM
                     etl_statistic t
                         LEFT JOIN
                     vw_etl_table_with_source b
                 on t.tid = b.id
                 WHERE
                     t.biz_date > current_date - 5
                 GROUP BY
                     b.code, t.biz_date
             ) sub
        GROUP BY
            biz_date
        ORDER BY
            biz_date;
        """, nativeQuery = true)
    List<Map<String, Object>> findLast5DaysTakeTimes();

    @Query(value = """
        select
                 biz_date,
                 array_agg(code) AS sources,
                 array_agg(total_bytes) AS total_bytes
             FROM (
                      SELECT
                          b.code,
                          t.biz_date,
                          round(SUM(total_bytes) / 1024 / 1024,2) AS total_bytes
                      FROM
                          etl_statistic t
                              LEFT JOIN
                          vw_etl_table_with_source b
                      on t.tid = b.id
                      WHERE
                          t.biz_date > current_date - INTERVAL '5 days'
                      GROUP BY
                          b.code, t.biz_date
                  ) sub
             GROUP BY
                 biz_date
        ORDER BY
            biz_date
        """, nativeQuery = true)
    List<Map<String, Object>> findLast5DaysDataMB();

    List<EtlStatistic> findTop15ByTidOrderByBizDateDesc(long tid);

    @Query(value = """
        SELECT id FROM etl_statistic
        WHERE (:source_tid IS NULL OR tid = :source_tid)
          AND (:before IS NULL OR start_at < :before)
        ORDER BY id
        LIMIT :limit
        """, nativeQuery = true)
    List<Long> findIdsToDeleteBatch(@org.springframework.data.repository.query.Param("source_tid") Long sourceTid,
        @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before,
        @org.springframework.data.repository.query.Param("limit") int limit);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "delete from etl_statistic where id in :ids", nativeQuery = true)
    int deleteByIdInBatch(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

    @Query(value = """
        select count(1) from etl_statistic
        where (:source_tid is null or tid = :source_tid)
          and (:before is null or start_at < :before)
        """, nativeQuery = true)
    long countToDelete(@org.springframework.data.repository.query.Param("source_tid") Long sourceTid,
        @org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before);
}
