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
    fun statLast12MonthsData(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
            select to_char(date_trunc('month', run_date), 'YYYY-MM') as month,
                   sum(total_bytes)/1024/1024/1024 as total_gb
            from etl_statistic
            where run_date >= date_trunc('month', current_date) - interval '11 months'
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

    //按采集源统计目前的采集状态统计
    fun statStatusBySource(): List<Map<String, Any>> = jdbcTemplate.queryForList(
        """
             SELECT code,
                    jsonb_build_object('Y', num1,'E', num2,'W', num3,'R', num4) AS status_stats,
                    num1 + num2 + num3 + num4 AS total
              FROM (
                SELECT code,
                       SUM(CASE WHEN status = 'Y' THEN 1 ELSE 0 END) AS num1,
                       SUM(CASE WHEN status = 'E' THEN 1 ELSE 0 END) AS num2,
                       SUM(CASE WHEN status = 'W' THEN 1 ELSE 0 END) AS num3,
                       SUM(case when status = 'R' then 1 else 0 END) as num4
                vw_etl_table_with_source
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
    ) as List<Map<String, Any>>

    // 目前有效的采集表数量
    fun statValidEtlTables(): Int = jdbcTemplate.queryForObject(
        """
            select count(*) from vw_etl_table_with_source where status <> 'X' and enabled  =  true
        """.trimIndent(),
        Int::class.java
    ) ?: 0

    fun saveOrUpdate(statistic: EtlStatistic): Boolean {
        val tid = statistic.tid ?: return false
        val runDate = statistic.runDate ?: return false
        etlStatisticRepo.findByTidAndRunDate(tid, runDate)!!.ifPresentOrElse({ existing ->
            statistic.id = existing?.id
            etlStatisticRepo.save(statistic)
        }, { etlStatisticRepo.save(statistic) })
        return true
    }

    fun findErrorTask(): List<EtlStatistic?>? = etlStatisticRepo.findErrorTask()

    // 根据采集表 ID 获取最近 15 条采集日志
    fun getLast15Records(tid: Long): List<EtlStatistic?>? = etlStatisticRepo.findTop15ByTidOrderByRunDateDesc(tid)

    val last2DaysCompleteList: List<Map<String, Any>>
        /**
         * 以数据源为单位，统计最近两天的采集完成情况
         * @return
         */
        get() = jdbcTemplate.queryForList(
            """
                with total_info as (
                    select s.code, max(start_at) as start_at, count(*) as total_cnt,
                        sum(case when status = 'Y' then 1 else 0 end) as succ_cnt,
                        sum(case when status = 'R' then 1 else 0 end) as run_cnt,
                        sum(case when status = 'E' then 1 else 0 end ) as fail_cnt,
                        sum(case when status = 'N' then 1 else 0 end ) as no_run_cnt,
                        sum(case when status = 'U' then 1 else 0 end ) as no_create_table_cnt
                    from vw_etl_table_with_source s where s.enabled = true group by s.code
                ),
                last2_info as (
                    select code, name,
                           max(case when rn = 1 then begin_at else null end) as y_begin_at ,
                           max(case when rn = 2 then begin_at else null end) as t_begin_at,
                           max(case when rn =1 then finish_at else null end) as y_finish_at,
                           max(case when rn =2 then finish_at else null end) as t_finish_at,
                           max(case when rn =1  then extract( epoch from finish_at - begin_at) else 0 end ) as y_take_secs,
                           max(case when rn =2  then extract(epoch from finish_at - begin_at) else 0 end ) as t_take_secs
                    from (
                        select code, name, run_date,
                               min(es.start_at) as begin_at, max(end_at) as finish_at,
                               row_number() over(partition by code order by run_date) as rn
                        from etl_statistic es
                        left join vw_etl_table_with_source vetws on es.tid = vetws.id
                        where run_date > now() - interval '10' day
                        group by vetws.code, vetws.name, run_date
                    )t where t.rn < 3 group by code,name
                )
                select b.name || '(' || b.code || ')' as sys_name,
                       a.start_at,
                       a.total_cnt, a.succ_cnt, a.run_cnt, a.fail_cnt, a.no_run_cnt, a.no_create_table_cnt,
                       to_char(b.y_begin_at, 'YYYY-mm-dd HH:MM:ss') as y_begin_at,
                       to_char(b.y_finish_at, 'YYYY-mm-dd HH:MM:ss') as y_finish_at,
                       b.y_take_secs,
                       to_char(b.t_begin_at, 'YYYY-mm-dd HH:MM:ss') as t_begin_at,
                       to_char(b.t_finish_at, 'YYYY-mm-dd HH:MM:ss') as t_finish_at,
                       b.t_take_secs
                from total_info a join last2_info b on a.code = b.code
            """.trimIndent()
        ) as List<Map<String, Any>>
}
