package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.model.EtlStatistic
import com.wgzhao.addax.admin.repository.EtlStatisticRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class StatService(
    private val jdbcTemplate: JdbcTemplate,
    private val etlStatisticRepo: EtlStatisticRepo
) {
    private val log = KotlinLogging.logger {}

    // 按采集源统计最近一次采集的数据量
    fun statDataBySource(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
            select b.code , max(b.name) as sourceName, sum(t.total_bytes) as total_bytes
            from (SELECT tid, total_bytes FROM (
              SELECT tid, total_bytes, row_number() OVER (PARTITION BY tid ORDER BY run_date DESC) AS rn
              FROM etl_statistic) t WHERE rn = 1) t
            left join vw_etl_table_with_source b on b.id = t.tid
            group by b.code
        """.trimIndent()
    ) as List<Map<String, Any>>

    // 最近一次采集的总数据量，单位 GB
    fun statTotalData(): Double = jdbcTemplate.queryForObject(
        """
            select coalesce(sum(t.total_bytes)/1024/1024/1024,0) as total_gb
            from (SELECT tid, total_bytes FROM (
              SELECT tid, total_bytes, row_number() OVER (PARTITION BY tid ORDER BY run_date DESC) AS rn
              FROM etl_statistic) t WHERE rn = 1) t
            left join etl_table a on a.id = t.tid
        """.trimIndent(),
        Double::class.java
    ) ?: 0.0

    // 最近 12个月的采集累计数据量，单位为 GiB
    // SQLite 适配：使用 strftime 代替 to_char，date('now', '-11 months') 代替 date_trunc 和 interval
    fun statLast12MonthsData(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
            select strftime('%Y-%m', run_date) as month,
                   sum(total_bytes)/1024/1024/1024 as total_gb
            from etl_statistic
            where run_date >= date('now', '-11 months')
            group by month
            order by month
        """.trimIndent()
    ) as List<Map<String, Any>>

    // 按采集来源统计最近一次采集的耗时
    fun statTimeBySource(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
            select code , max(name) as sourceName, sum(duration) as take_secs
            from vw_etl_table_with_source
            group by code
        """.trimIndent()
    ) as List<Map<String, Any>>

    // 按照采集来源统计最近 5 天的耗时，用来形成柱状图表
    fun statLast5DaysTimeBySource(): List<Map<String, Any>> =
        (etlStatisticRepo.findLast5DaysTakeTimes() ?: emptyList()) as List<Map<String, Any>>

    // 按采集源统计目前的采集状态统计
    // SQLite 适配：使用 json_object 代替 jsonb_build_object
    fun statStatusBySource(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
             SELECT code,
                    json_object('Y', num1,'E', num2,'W', num3,'R', num4) AS status_stats,
                    num1 + num2 + num3 + num4 AS total
              FROM (
                SELECT code,
                       SUM(CASE WHEN status = 'Y' THEN 1 ELSE 0 END) AS num1,
                       SUM(CASE WHEN status = 'E' THEN 1 ELSE 0 END) AS num2,
                       SUM(CASE WHEN status = 'W' THEN 1 ELSE 0 END) AS num3,
                       SUM(case when status = 'R' then 1 else 0 END) as num4
                FROM vw_etl_table_with_source
                GROUP BY code
              ) AS final;
        """.trimIndent()
    ) as List<Map<String, Any>>

    // 最近采集的完成率
    fun statLastAccompRatio(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
            SELECT name || '(' || code || ')' AS source_name,
                   ROUND(over_prec_percent, 0) || '%' AS over_prec_str,
                   CASE
                       WHEN over_prec_percent >= 100 THEN 'bg-success'
                       WHEN over_prec_percent <= 40 THEN 'bg-danger'
                       WHEN over_prec_percent <= 60 THEN 'bg-warning'
                       ELSE 'bg-info'
                   END AS bg_color
            FROM (
                SELECT t.code, t.name,
                       (SUM(CASE WHEN t.status = 'Y' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) AS over_prec_percent
                FROM vw_etl_table_with_source t
                GROUP BY t.code, t.name
                having count(*) > 0
            ) AS a
        """.trimIndent()
    )

    fun saveOrUpdate(statistic: EtlStatistic): Boolean {
        val tid = statistic.tid ?: return false
        val runDate = statistic.runDate ?: return false
        val id = etlStatisticRepo.findByTidAndRunDate(tid, runDate)?.id
        id?.let {
            statistic.id = it
        }
        etlStatisticRepo.save(statistic)
        return true
    }

    fun findErrorTask(): List<EtlStatistic?>? = etlStatisticRepo.findErrorTask()

    // 根据采集表 ID 获取最近 15 条采集日志
    fun getLast15Records(tid: Long): List<EtlStatistic?>? = etlStatisticRepo.findTop15ByTidOrderByRunDateDesc(tid)

    /**
     * 以数据源为单位，统计最近两天的采集完成情况
     * @return 最近两天的采集完成情况列表
     */
    fun last2DaysCompleteList(): List<Map<String, Any>> =
        jdbcTemplate.queryForList(
            """
               WITH total_info AS (
                   SELECT s.code, MAX(start_at) AS start_at, COUNT(*) AS total_cnt,
                          SUM(CASE WHEN status = 'Y' THEN 1 ELSE 0 END) AS succ_cnt,
                          SUM(CASE WHEN status = 'R' THEN 1 ELSE 0 END) AS run_cnt,
                          SUM(CASE WHEN status = 'E' THEN 1 ELSE 0 END) AS fail_cnt,
                          SUM(CASE WHEN status = 'N' THEN 1 ELSE 0 END) AS no_run_cnt,
                          SUM(CASE WHEN status = 'U' THEN 1 ELSE 0 END) AS no_create_table_cnt
                   FROM vw_etl_table_with_source s
                   WHERE s.enabled = 1
                   GROUP BY s.code
               ),
               last2_info AS (
                   SELECT code, name,
                          MAX(CASE WHEN rn = 1 THEN begin_at ELSE NULL END) AS y_begin_at,
                          MAX(CASE WHEN rn = 2 THEN begin_at ELSE NULL END) AS t_begin_at,
                          MAX(CASE WHEN rn = 1 THEN finish_at ELSE NULL END) AS y_finish_at,
                          MAX(CASE WHEN rn = 2 THEN finish_at ELSE NULL END) AS t_finish_at,
                          MAX(CASE WHEN rn = 1 THEN (julianday(finish_at) - julianday(begin_at)) * 86400 ELSE 0 END) AS y_take_secs,
                          MAX(CASE WHEN rn = 2 THEN (julianday(finish_at) - julianday(begin_at)) * 86400 ELSE 0 END) AS t_take_secs
                   FROM (
                       SELECT vetws.code, vetws.name, run_date,
                              MIN(es.start_at) AS begin_at, MAX(end_at) AS finish_at,
                              ROW_NUMBER() OVER (PARTITION BY vetws.code ORDER BY run_date) AS rn
                       FROM etl_statistic es
                       LEFT JOIN vw_etl_table_with_source vetws ON es.tid = vetws.id
                       WHERE run_date > date('now', '-10 days')
                       GROUP BY vetws.code, vetws.name, run_date
                   ) t
                   WHERE t.rn < 3
                   GROUP BY code, name
               )
               SELECT b.name || '(' || b.code || ')' AS sys_name,
                      a.start_at,
                      a.total_cnt, a.succ_cnt, a.run_cnt, a.fail_cnt, a.no_run_cnt, a.no_create_table_cnt,
                      strftime('%Y-%m-%d %H:%M:%S', b.y_begin_at) AS y_begin_at,
                      strftime('%Y-%m-%d %H:%M:%S', b.y_finish_at) AS y_finish_at,
                      b.y_take_secs,
                      strftime('%Y-%m-%d %H:%M:%S', b.t_begin_at) AS t_begin_at,
                      strftime('%Y-%m-%d %H:%M:%S', b.t_finish_at) AS t_finish_at,
                      b.t_take_secs
               FROM total_info a
               JOIN last2_info b ON a.code = b.code
            """.trimIndent()
        )
}
