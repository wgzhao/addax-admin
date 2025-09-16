package com.wgzhao.addax.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AddaxStatService
{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 按采集源统计最近一次采集的数据量
    public List<Map<String, Object>> statDataBySource()
    {
        String sql = """
                select
                b.db_id_etl ,
                max(b.db_name) as sourceName,
                sum(t.total_bytes) as total_bytes
                from
                (SELECT tid, total_bytes FROM (
                  SELECT
                    tid, total_bytes,
                    row_number() OVER (PARTITION BY tid ORDER BY update_at DESC) AS rn
                  FROM tb_addax_statistic
                ) t WHERE rn = 1)
                t
                left join
                tb_imp_etl a
                on a.tid = t.tid
                left join tb_imp_db b
                on a.sou_sysid  = b.db_id_etl
                group by b.db_id_etl
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 最近一次采集的总数据量，单位 GB
    public Double statTotalData()
    {
        String sql = """
                select
                sum(t.total_bytes)/1024/1024/1024 as total_gb
                from
                (SELECT tid, total_bytes FROM (
                  SELECT
                    tid, total_bytes,
                    row_number() OVER (PARTITION BY tid ORDER BY update_at DESC) AS rn
                  FROM tb_addax_statistic
                ) t WHERE rn = 1)
                t
                left join
                tb_imp_etl a
                on a.tid = t.tid
                """;
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    // 最近 12个月的采集累计数据量，单位为 GiB
    public List<Map<String, Object>> statLast12MonthsData()
    {
        String sql = """
                select
                to_char(date_trunc('month', update_at), 'YYYY-MM') as month,
                sum(total_bytes)/1024/1024/1024 as total_gb
                from tb_addax_statistic
                where update_at >= date_trunc('month', current_date) - interval '11 months'
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
                b.db_id_etl ,
                max(b.db_name) as sourceName,
                sum(a.runtime) as take_secs
                from
                tb_imp_etl a
                left join tb_imp_db b
                on a.sou_sysid  = b.db_id_etl
                group by b.db_id_etl
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 按照采集来源统计最近 5 天的耗时，用来形成柱状图表
    public List<Map<String, Object>> statLast5DaysTimeBySource(int l5td)
    {
        String sql = """
                select
                b.db_id_etl ,
                max(b.db_name) as sourceName,
                a.tradedate,
                sum(a.runtime) as take_secs
                from
                (select tradedate,fid,
                       extract(epoch from (max(case when fval=4 then dw_clt_date end) -
                       	max(case when fval=3 then dw_clt_date end))) runtime
                from tb_imp_flag
                where kind in('ETL_END','ETL_START') and tradedate>=?1 and fval in(3,4)
                group by tradedate,fid
                having max(case when fval=3 then 1 else 0 end)=max(case when fval=4 then 1 else 0 end)
                ) a
                left join tb_imp_etl c
                on a.fid = c.tid
                left join tb_imp_db b
                on c.sou_sysid  = b.db_id_etl
                group by b.db_id_etl,a.tradedate
                order by b.db_id_etl,a.tradedate
                """;
        return jdbcTemplate.queryForList(sql, l5td);
    }

    //按采集源统计目前的采集状态统计
    public List<Map<String, Object>> statStatusBySource()
    {
        String sql = """
                
                 SELECT
                    db_id_etl,
                    jsonb_build_object(
                        'Y', num1,
                        'E', num2,
                        'W', num3,
                        'R', num4
                    ) AS status_stats,
                    num1 + num2 + num3 + num4 AS total
                FROM (
                    SELECT
                        db_id_etl,
                        SUM(CASE WHEN flag = 'Y' THEN 1 ELSE 0 END) AS num1,
                        SUM(CASE WHEN flag = 'E' THEN 1 ELSE 0 END) AS num2,
                        SUM(CASE WHEN flag = 'W' THEN 1 ELSE 0 END) AS num3,
                        SUM(case when flag = 'R' then 1 else 0 END) as num4
                    FROM (
                        SELECT
                            a.flag,
                            b.db_id_etl AS db_id_etl,
                            b.db_name
                        FROM tb_imp_etl a
                        LEFT JOIN tb_imp_db b
                        ON a.sou_sysid = b.db_id_etl
                    ) AS sub
                    GROUP BY db_id_etl
                ) AS final;
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 最近采集的完成率
    public List<Map<String, Object>> statLastAccompRatio()
    {
        String sql = """
                SELECT
                    sou_sysid || '_' || db_name AS source_name,
                    ROUND(over_prec_percent, 0) || '%' AS over_prec_str,
                    CASE
                        WHEN over_prec_percent >= 100 THEN 'bg-success'
                        WHEN over_prec_percent <= 40 THEN 'bg-danger'
                        WHEN over_prec_percent <= 60 THEN 'bg-warning'
                        ELSE 'bg-info'
                    END AS bg_color
                FROM (
                    SELECT
                        t.sou_sysid,
                        d.db_name,
                        SUM(CASE WHEN t.flag = 'Y' THEN 1 ELSE 0 END) AS finish_num,
                        COUNT(*) AS total_num,
                        (SUM(CASE WHEN t.flag = 'Y' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) AS over_prec_percent
                    FROM tb_imp_etl t
                    LEFT JOIN tb_imp_db d
                    ON t.sou_sysid = d.db_id_etl
                    GROUP BY t.sou_sysid, d.db_name
                ) AS a
                WHERE total_num > 0;
                """;
        return jdbcTemplate.queryForList(sql);
    }
}
