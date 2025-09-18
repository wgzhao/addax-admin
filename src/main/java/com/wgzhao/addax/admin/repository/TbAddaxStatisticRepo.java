package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbAddaxStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TbAddaxStatisticRepo
        extends JpaRepository<TbAddaxStatistic, Long>
{

    Optional<TbAddaxStatistic> findByTidAndRunDate(String tid, LocalDate runDate);

    @Query(value = """
            SELECT s1 FROM TbAddaxStatistic s1
            WHERE s1.totalErrors > 0
            AND s1.runDate = (
                SELECT MAX(s2.runDate)
                FROM TbAddaxStatistic s2
                WHERE s2.tid = s1.tid
            )
            """)
    List<TbAddaxStatistic> findErrorTask();

    @Query(value = """
             select
                 run_date,
                 array_agg(db_id_etl) AS sources,
                 array_agg(total_secs) AS total_secs
             FROM (
                 SELECT
                     b.db_id_etl,
                     t.run_date,
                     SUM(t.take_secs) AS total_secs
                 FROM
                     tb_addax_statistic t
                 LEFT JOIN
                     tb_imp_etl a
                     ON a.tid = t.tid
                 LEFT JOIN
                     tb_imp_db b
                     ON a.sou_sysid = b.db_id_etl
                 WHERE
                     t.run_date > current_date - INTERVAL '5 days'
                 GROUP BY
                     b.db_id_etl, t.run_date
             ) sub
             GROUP BY
                 run_date
            """, nativeQuery = true)
    List<Map<String, Object>> findLast5DaysTakeTimes();

    List<TbAddaxStatistic> findTop15ByTidOrderByRunDateDesc(String tid);
}
