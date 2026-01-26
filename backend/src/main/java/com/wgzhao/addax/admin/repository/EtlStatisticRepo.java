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
                     t.biz_date >= current_date - 5
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
                          t.biz_date >= current_date - INTERVAL '5 days'
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

    Optional<EtlStatistic> findTop1ByTidOrderByBizDateDesc(long tid);
}
