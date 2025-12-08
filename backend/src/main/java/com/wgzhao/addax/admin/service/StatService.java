package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.repository.EtlStatisticRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class StatService {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EtlStatisticRepo etlStatisticRepo;

    @Autowired
    private SystemConfigService configService;

    // 按采集源统计最近一次采集的数据量
    public List<Map<String, Object>> statDataBySource() {
        String sql = """
                select
                b.code ,
                max(b.name) as sourceName,
                sum(t.total_bytes) as total_bytes
                from
                (SELECT tid, total_bytes FROM (
                  SELECT
                    tid, total_bytes,
                    row_number() OVER (PARTITION BY tid ORDER BY biz_date DESC) AS rn
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
    public Double statTotalData() {
        LocalDate bizDate = configService.getBizDateAsDate();
        String sql = """
                select
                round(coalesce(sum(total_bytes)/1024/1024/1024,0),2) as total_gb
                from etl_statistic
                where biz_date = ?
                """;
        return jdbcTemplate.queryForObject(sql, Double.class, bizDate);
    }

    // 最近 12个月的采集累计数据量，单位为 GiB
    public List<Map<String, Object>> statLast12MonthsData() {
        String sql = """
                select
                to_char(date_trunc('month', biz_date), 'YYYY-MM') as month,
                sum(total_bytes)/1024/1024/1024 as total_gb
                from etl_statistic
                where biz_date >= date_trunc('month', current_date) - interval '11 months'
                group by month
                order by month
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 按采集源统计最近一次采集的耗时
    public List<Map<String, Object>> statTimeBySource() {
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
    public List<Map<String, Object>> statLast5DaysTimeBySource() {
        return etlStatisticRepo.findLast5DaysTakeTimes();
    }

    //按采集源统计目前的采集状态统计
    public List<Map<String, Object>> statStatusBySource() {
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
    public List<Map<String, Object>> statLastAccompRatio() {
        String sql = """
                SELECT
                    name || '(' || code || ')' AS source_name,
                    round(SUM(CASE WHEN t.status = 'Y' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 0) AS pct
                FROM vw_etl_table_with_source t
                WHERE t.status <> 'X' and t.enabled = true
                GROUP BY t.code, t.name
                having count(*) > 0
                """;
        return jdbcTemplate.queryForList(sql);
    }

    // 目前有效的采集表数量
    public Integer statValidEtlTables() {
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
        etlStatisticRepo.findByTidAndBizDate(statistic.getTid(), statistic.getBizDate())
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

    public List<EtlStatistic> findErrorTask() {
        return etlStatisticRepo.findErrorTask();
    }

    // 根据采集表 ID 获取最近 15 条采集日志
    public List<EtlStatistic> getLast15Records(long tid) {
        return etlStatisticRepo.findTop15ByTidOrderByBizDateDesc(tid);
    }

    /**
     * 已数据源为单位，统计最近两天的采集完成情况
     *
     * @return List
     */
    public List<Map<String, Object>> getLast2DaysCompleteList() {
        String sql = """
                with total_info as (
                    select
                    s.code,
                    max(start_at) as start_at,
                    count(*) as total_cnt,
                    sum(case when status = 'Y' then 1 else 0 end) as succ_cnt,
                    sum(case when status = 'R' then 1 else 0 end) as run_cnt,
                    sum(case when status = 'E' then 1 else 0 end ) as fail_cnt,
                    sum(case when status = 'N' then 1 else 0 end ) as no_run_cnt,
                    sum(case when status = 'U' then 1 else 0 end ) as no_create_table_cnt
                    from
                    vw_etl_table_with_source s
                    where s.enabled = true and s.status <> 'X'
                    group by s.code
                ),
                last2_info as (
                    select code, name,
                           max(t.y_begin_at)   as y_begin_at,
                            max(t.y_finish_at)  as y_finish_at,
                            max(t.y_take_secs)   as y_take_secs,
                            max(t.t_begin_at)   as t_begin_at,
                            max(t.t_finish_at)  as t_finish_at,
                            max(t.t_take_secs)   as t_take_secs
                        from (select code,
                                     name,
                                     min(e.start_at)                                     as y_begin_at,
                                     null                                                as t_begin_at,
                                     max(e.end_at)                                       as y_finish_at,
                                     null                                                as t_finish_at,
                                     extract(epoch from max(e.end_at) - min(e.start_at)) as y_take_secs,
                                     0                                                   as t_take_secs
                              from etl_statistic e
                                       left join vw_etl_table_with_source s
                                                 on e.tid = s.id
                              where biz_date = ?
                                and s.enabled = true
                                and s.status <> 'X'
                              group by s.code, s.name, biz_date
                              union all
                              select code,
                                     name,
                                     null                                                as y_begin_at,
                                     min(e.start_at)                                     as t_begin_at,
                                     null                                                as y_finish_at,
                                     max(e.end_at)                                       as t_finish_at,
                                     0                                                   as y_take_secs,
                                     extract(epoch from max(e.end_at) - min(e.start_at)) as t_take_secs
                              from etl_statistic e
                                       left join vw_etl_table_with_source s
                                                 on e.tid = s.id
                              where biz_date = ?
                                and s.enabled = true
                                and s.status <> 'X'
                              group by s.code, s.name, biz_date) t
                        group by t.code, t.name
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
                from total_info a
                join last2_info b
                on a.code = b.code
                """;
        return jdbcTemplate.queryForList(sql, configService.getBizDateAsDate().plusDays(-1),
                configService.getBizDateAsDate());
    }

    public Double statAllTotalData() {
        String sql = """
                select
                   round(sum(total_bytes)/1024/1024/1024,2) as total_gb
                from etl_statistic
                """;
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    public List<Map<String, Object>> statLast5DaysDataBySource() {
        return etlStatisticRepo.findLast5DaysDataMB();
    }

    public List<Map<String, Object>> getNoTableRowsChangeList(int days) {
        String sql = """
                WITH latest_per_day AS (
                  SELECT DISTINCT ON (tid, biz_date)
                    tid,
                    biz_date,
                    total_recs
                  FROM etl_statistic
                  where biz_date >  date(now() - interval '?' day)
                  ORDER BY tid, biz_date DESC
                ),
                last_t AS (
                  SELECT
                    tid,
                    biz_date,
                    total_recs,
                    ROW_NUMBER() OVER (PARTITION BY tid ORDER BY biz_date DESC) AS rn
                  FROM latest_per_day
                )
                SELECT l.tid, min(biz_date) as start_date, max(biz_date) as end_date, 
                       max(t.source_db), max(t.source_table), max(total_recs) as total_recs
                FROM last_t l
                join etl_table t
                on l.tid = t.id
                WHERE l.rn <= 5 and t.status <> 'X'
                GROUP BY l.tid
                HAVING COUNT(*) = ?
                   AND COUNT(DISTINCT total_recs) = 1
                """;
        return jdbcTemplate.queryForList(sql, days + 3, days);
    }
}
