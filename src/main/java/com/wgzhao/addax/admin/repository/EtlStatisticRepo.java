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

    Optional<EtlStatistic> findByTidAndRunDate(long tid, LocalDate runDate);

    @Query(value = """
            SELECT s1 FROM EtlStatistic s1
            WHERE s1.totalErrors > 0
            AND s1.runDate = (
                SELECT MAX(s2.runDate)
                FROM EtlStatistic s2
                WHERE s2.tid = s1.tid
            )
            """)
    List<EtlStatistic> findErrorTask();

    @Query(value = """
             select
                 run_date,
                 array_agg(code) AS sources,
                 array_agg(total_secs) AS total_secs
             FROM (
                      SELECT
                          b.code,
                          t.run_date,
                          SUM(t.take_secs) AS total_secs
                      FROM
                          etl_statistic t
                              LEFT JOIN
                          vw_etl_table_with_source b
                      on t.tid = b.id
                      WHERE
                          t.run_date > current_date - INTERVAL '5 days'
                      GROUP BY
                          b.code, t.run_date
                  ) sub
             GROUP BY
                 run_date
            """, nativeQuery = true)
    List<Map<String, Object>> findLast5DaysTakeTimes();

    List<EtlStatistic> findTop15ByTidOrderByRunDateDesc(long tid);
}
