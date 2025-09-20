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
                b.db_id_etl ,
                max(b.db_name) as sourceName,
                sum(t.total_bytes) as total_bytes
                from
                (SELECT tid, total_bytes FROM (
                  SELECT
                    tid, total_bytes,
                    row_number() OVER (PARTITION BY tid ORDER BY run_date DESC) AS rn
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
                    row_number() OVER (PARTITION BY tid ORDER BY run_date DESC) AS rn
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
                to_char(date_trunc('month', run_date), 'YYYY-MM') as month,
                sum(total_bytes)/1024/1024/1024 as total_gb
                from tb_addax_statistic
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
    public List<Map<String, Object>> statLast5DaysTimeBySource()
    {
        String sql = """
               
                """;
        return etlStatisticRepo.findLast5DaysTakeTimes();
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

    // 目前有效的采集表数量
    public Integer statValidEtlTables()
    {
        String sql = """
                select
                count(*)
                from tb_imp_etl t
                join tb_imp_db d
                on t.sou_sysid = d.db_id_etl
                where t.flag <> 'X' and d.bvalid  = 'Y'
                """;
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    // 目前有效的采集源数量
    public Integer statValidEtlSources()
    {
        String sql = """
                select
                count(*)
                from tb_imp_db
                where bvalid  = 'Y'
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
    public List<EtlStatistic> getLast15Records(String tid) {
        return etlStatisticRepo.findTop15ByTidOrderByRunDateDesc(tid);
    }
}
