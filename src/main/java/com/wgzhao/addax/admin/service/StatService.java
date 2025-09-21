package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.repository.EtlStatisticRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StatService
{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EtlStatisticRepo etlStatisticRepo;

    // 按采集源统计最近一次采集的数据量
    public List<Map<String, Object>> statDataBySource()
    {
        String sql = """
                select
                b.code ,
                max(b.name) as sourceName,
                sum(t.total_bytes) as total_bytes
                from
                (SELECT tid, total_bytes FROM (
                  SELECT
                    tid, total_bytes,
                    row_number() OVER (PARTITION BY tid ORDER BY run_date DESC) AS rn
                  FROM etl_statistic
                ) t WHERE rn = 1)
                t
                left join
                vw_etl_table_with_source b
                on b.id = t.tid
                group by b.code
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 最近一次采集的总数据量，单位 GB
    public Double statTotalData()
    {
        String sql = """
                select
                coalesce(sum(t.total_bytes)/1024/1024/1024,0) as total_gb
                from
                (SELECT tid, total_bytes FROM (
                  SELECT
                    tid, total_bytes,
                    row_number() OVER (PARTITION BY tid ORDER BY run_date DESC) AS rn
                  FROM etl_statistic
                ) t WHERE rn = 1)
                t
                left join
                etl_table a
                on a.id = t.tid
                """;
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    // 最近 12个月的采集累计数据量，单位为 GiB
    public List<Map<String, Object>> statLast12MonthsData()
    {
        String sql = """
                select
                to_char(date_trunc('month', run_date), 'YYYY-MM') as month,
                sum(total_bytes)/1024/1024/1024 as total_gb
                from etl_statistic
                where run_date >= date_trunc('month', current_date) - interval '11 months'
                group by month
                order by month
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 按采集源统计最近一次采集的耗时
    public List<Map<String, Object>> statTimeBySource()
    {
        String sql = """
                select
                code ,
                max(name) as sourceName,
                sum(duration) as take_secs
                from
                vw_etl_table_with_source
                group by code
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 按照采集来源统计最近 5 天的耗时，用来形成柱状图表
    public List<Map<String, Object>> statLast5DaysTimeBySource()
    {
        return etlStatisticRepo.findLast5DaysTakeTimes();
    }

    //按采集源统计目前的采集状态统计
    public List<Map<String, Object>> statStatusBySource()
    {
        String sql = """
                 SELECT
                    code,
                    jsonb_build_object(
                        'Y', num1,
                        'E', num2,
                        'W', num3,
                        'R', num4
                    ) AS status_stats,
                    num1 + num2 + num3 + num4 AS total
                FROM (
                    SELECT
                        code,
                        SUM(CASE WHEN status = 'Y' THEN 1 ELSE 0 END) AS num1,
                        SUM(CASE WHEN status = 'E' THEN 1 ELSE 0 END) AS num2,
                        SUM(CASE WHEN status = 'W' THEN 1 ELSE 0 END) AS num3,
                        SUM(case when status = 'R' then 1 else 0 END) as num4
                    vw_etl_table_with_source
                    GROUP BY code
                ) AS final;
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 最近采集的完成率
    public List<Map<String, Object>> statLastAccompRatio()
    {
        String sql = """
                SELECT
                    name || '(' || code || ')' AS source_name,
                    ROUND(over_prec_percent, 0) || '%' AS over_prec_str,
                    CASE
                        WHEN over_prec_percent >= 100 THEN 'bg-success'
                        WHEN over_prec_percent <= 40 THEN 'bg-danger'
                        WHEN over_prec_percent <= 60 THEN 'bg-warning'
                        ELSE 'bg-info'
                        END AS bg_color
                FROM (
                         SELECT
                             t.code,
                             t.name,
                             (SUM(CASE WHEN t.status = 'Y' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) AS over_prec_percent
                         FROM vw_etl_table_with_source t
                         GROUP BY t.code, t.name
                         having count(*) > 0
                     ) AS a
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 目前有效的采集表数量
    public Integer statValidEtlTables()
    {
        String sql = """
                select
                count(*)
                from
                vw_etl_table_with_source
                where status <> 'X' and enabled  =  true
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    public boolean saveOrUpdate(EtlStatistic statistic) {
        etlStatisticRepo.findByTidAndRunDate(statistic.getTid(), statistic.getRunDate())
                .ifPresentOrElse(existingStat -> {
                    // 如果存在，则更新现有记录
                    statistic.setId(existingStat.getId());
                    etlStatisticRepo.save(statistic);
                }, () -> {
                    // 如果不存在，则插入新记录
                    etlStatisticRepo.save(statistic);
                });
        return true;
    }

    public List<EtlStatistic> findErrorTask()
    {
        return etlStatisticRepo.findErrorTask();
    }

    // 根据采集表 ID 获取最近 15 条采集日志
    public List<EtlStatistic> getLast15Records(long tid) {
        return etlStatisticRepo.findTop15ByTidOrderByRunDateDesc(tid);
    }
}
