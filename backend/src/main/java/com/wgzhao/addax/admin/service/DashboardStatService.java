package com.wgzhao.addax.admin.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 首页新增图表的聚合查询服务。
 * <p>
 * 两类时间口径：
 * <ul>
 *   <li>基于 etl_statistic 的查询以配置的业务日期（biz_date）为窗口锚点，与 StatService 保持一致；</li>
 *   <li>基于 etl_jour 的查询以挂钟时间（current_date）为窗口，因为该表没有 biz_date 维度，
 *       且业务日 D 的任务可能在 D+1 凌晨才执行。</li>
 * </ul>
 */
@Service
@AllArgsConstructor
public class DashboardStatService
{
    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigService configService;

    /**
     * 业务日期锚点，集中一处便于环境调整
     */
    private LocalDate anchor()
    {
        return configService.getBizDateAsDate();
    }

    // 当前采集表状态分布（实时），用于环形图
    public List<Map<String, Object>> statTableStatusDistribution()
    {
        String sql = """
            select status, count(*) as cnt
            from vw_etl_table_with_source
            group by status
            order by status
            """;
        return jdbcTemplate.queryForList(sql);
    }

    // 近 14 天按操作类型统计的失败次数，用于堆叠柱状图
    public List<Map<String, Object>> statLast14DaysFailTrend()
    {
        String sql = """
            with span as (
              select generate_series(current_date - 13, current_date, interval '1 day')::date as day
            ),
            fails as (
              select j.start_at::date as day,
                     j.kind as kind,
                     count(*) as cnt
              from etl_jour j
              where j.status = false
                and j.start_at >= (current_date - 13)::timestamp
              group by 1, 2
            )
            select to_char(s.day, 'YYYY-MM-DD') as day,
                   f.kind as kind,
                   coalesce(f.cnt, 0) as cnt
            from span s
            left join fails f on f.day = s.day
            order by s.day, f.kind
            """;
        return jdbcTemplate.queryForList(sql);
    }

    // 近 N 天失败次数最多的采集任务，用于失败排行表
    public List<Map<String, Object>> statTopFailTables(int days, int limit)
    {
        int offset = Math.max(days - 1, 0);
        String sql = """
            with fails as (
              select tid,
                     start_at,
                     kind,
                     left(coalesce(error_msg, ''), 160) as error_msg,
                     count(*) over (partition by tid) as fail_cnt
              from etl_jour
              where status = false
                and start_at >= (current_date - ?)::timestamp
            ),
            ranked as (
              select distinct on (tid)
                     tid,
                     fail_cnt,
                     start_at as last_fail_at,
                     kind as last_kind,
                     error_msg as last_error
              from fails
              order by tid, start_at desc
            )
            select v.id as tid,
                   v.source_db,
                   v.source_table,
                   v.target_db,
                   v.target_table,
                   v.code as source_code,
                   v.name as source_name,
                   v.retry_cnt,
                   r.fail_cnt,
                   r.last_kind,
                   to_char(r.last_fail_at, 'YYYY-MM-DD HH24:MI:SS') as last_fail_at,
                   r.last_error
            from ranked r
            join vw_etl_table_with_source v on v.id = r.tid
            order by r.fail_cnt desc, r.last_fail_at desc
            limit ?
            """;
        return jdbcTemplate.queryForList(sql, offset, limit);
    }

    // 近 30 天每日错误行占比，用于折线图
    public List<Map<String, Object>> statLast30DaysErrorRate()
    {
        LocalDate bizDate = anchor();
        String sql = """
            with span as (
              select generate_series(?::date - 29, ?::date, interval '1 day')::date as biz_date
            ),
            daily as (
              select biz_date,
                     sum(total_recs) as total_recs,
                     sum(total_errors) as total_errors
              from etl_statistic
              where biz_date > ?::date - 30
                and biz_date <= ?::date
              group by biz_date
            )
            select to_char(s.biz_date, 'YYYY-MM-DD') as biz_date,
                   coalesce(d.total_recs, 0) as total_recs,
                   coalesce(d.total_errors, 0) as total_errors,
                   round(coalesce(d.total_errors, 0) * 100.0 / nullif(d.total_recs, 0), 4) as error_pct
            from span s
            left join daily d on d.biz_date = s.biz_date
            order by s.biz_date
            """;
        return jdbcTemplate.queryForList(sql, bizDate, bizDate, bizDate, bizDate);
    }

    // 近 N 天每日应采/实采/缺失表数，用于采集缺失日历
    public List<Map<String, Object>> statMissingCollectCalendar(int days)
    {
        int offset = Math.max(days - 1, 0);
        LocalDate bizDate = anchor();
        String sql = """
            with span as (
              select generate_series(?::date - ?, ?::date, interval '1 day')::date as biz_date
            ),
            valid_tables as (
              select id as tid,
                     coalesce(created_at::date, ?::date) as created_date
              from vw_etl_table_with_source
              where status <> 'X'
                and coalesce(enabled, false) = true
            ),
            expected as (
              select s.biz_date, count(*) as expected
              from span s
              join valid_tables t on t.created_date <= s.biz_date
              group by s.biz_date
            ),
            actual as (
              select e.biz_date, count(distinct e.tid) as collected
              from etl_statistic e
              join valid_tables t on t.tid = e.tid
              where e.biz_date > ?::date - ?
                and e.biz_date <= ?::date
              group by e.biz_date
            )
            select to_char(x.biz_date, 'YYYY-MM-DD') as biz_date,
                   x.expected,
                   coalesce(a.collected, 0) as collected,
                   greatest(x.expected - coalesce(a.collected, 0), 0) as missing
            from expected x
            left join actual a on a.biz_date = x.biz_date
            order by x.biz_date
            """;
        return jdbcTemplate.queryForList(sql,
            bizDate, offset, bizDate,
            bizDate,
            bizDate, days, bizDate);
    }

    // 近 12 个月每月的记录数与数据量（GiB），用于双轴图。注意是每月值，非累计值
    public List<Map<String, Object>> statLast12MonthsRecsAndBytes()
    {
        LocalDate bizDate = anchor();
        String sql = """
            with months as (
              select date_trunc('month', ?::date) - interval '11 months'
                     + (interval '1 month' * gs) as month_start
              from generate_series(0, 11) as gs
            ),
            monthly as (
              select date_trunc('month', biz_date) as month_start,
                     sum(total_recs) as total_recs,
                     sum(total_bytes) as total_bytes
              from etl_statistic
              where biz_date >= date_trunc('month', ?::date) - interval '11 months'
              group by 1
            )
            select to_char(m.month_start, 'YYYY-MM') as month,
                   coalesce(x.total_recs, 0) as total_recs,
                   round(coalesce(x.total_bytes, 0) / 1024.0 / 1024 / 1024, 2) as total_gb
            from months m
            left join monthly x on x.month_start = m.month_start
            order by m.month_start
            """;
        return jdbcTemplate.queryForList(sql, bizDate, bizDate);
    }

    // 近 30 天各数据源每日采集量（GiB）长表，Top8 + 其他，用于堆叠面积图
    public List<Map<String, Object>> statLast30DaysSourceContribution()
    {
        LocalDate bizDate = anchor();
        String sql = """
            with win as (
              select v.code,
                     min(v.name) as name,
                     e.biz_date,
                     sum(e.total_bytes) as bytes
              from etl_statistic e
              join vw_etl_table_with_source v on v.id = e.tid
              where e.biz_date > ?::date - 30
                and e.biz_date <= ?::date
              group by v.code, e.biz_date
            ),
            top_sources as (
              select code
              from win
              group by code
              order by sum(bytes) desc
              limit 8
            ),
            rolled as (
              select w.biz_date,
                     case when t.code is null then 'OTHER' else t.code end as code,
                     case when t.code is null then '其他' else n.name end as name,
                     sum(w.bytes) as bytes
              from win w
              left join top_sources t on t.code = w.code
              left join (select code, min(name) as name from win group by code) n on n.code = w.code
              group by w.biz_date, t.code, n.name
            ),
            span as (
              select generate_series(?::date - 29, ?::date, interval '1 day')::date as biz_date
            )
            select to_char(s.biz_date, 'YYYY-MM-DD') as biz_date,
                   r.code,
                   r.name,
                   round(coalesce(r.bytes, 0) / 1024.0 / 1024 / 1024, 3) as gib
            from span s
            left join rolled r on r.biz_date = s.biz_date
            order by s.biz_date, r.code
            """;
        return jdbcTemplate.queryForList(sql, bizDate, bizDate, bizDate, bizDate);
    }

    // 近 N 天平均耗时最长的采集任务，用于慢表排行
    public List<Map<String, Object>> statTopSlowTables(int days, int limit)
    {
        int offset = Math.max(days - 1, 0);
        LocalDate bizDate = anchor();
        String sql = """
            with agg as (
              select tid,
                     count(*) as days,
                     sum(take_secs) as total_secs,
                     max(take_secs) as max_secs,
                     round(avg(take_secs)) as avg_secs
              from etl_statistic
              where biz_date > ?::date - ?
                and biz_date <= ?::date
                and take_secs is not null
              group by tid
            )
            select v.id as tid,
                   v.source_db,
                   v.source_table,
                   v.target_db,
                   v.target_table,
                   v.code as source_code,
                   v.name as source_name,
                   a.days,
                   a.avg_secs,
                   a.max_secs,
                   a.total_secs
            from agg a
            join vw_etl_table_with_source v on v.id = a.tid
            order by a.avg_secs desc
            limit ?
            """;
        return jdbcTemplate.queryForList(sql, bizDate, days, bizDate, limit);
    }

    // 近 N 天采集耗时分布直方图及 P50/P95 分位数
    public Map<String, Object> statTakeSecsHistogram(int days)
    {
        LocalDate bizDate = anchor();
        String sql = """
            with vals as (
              select take_secs
              from etl_statistic
              where biz_date > ?::date - ?
                and biz_date <= ?::date
                and take_secs is not null
            ),
            stats as (
              select percentile_cont(0.5) within group (order by take_secs) as p50,
                     percentile_cont(0.95) within group (order by take_secs) as p95,
                     max(take_secs) as max_secs,
                     count(*) as total
              from vals
            )
            select w.idx,
                   w.label,
                   w.lo,
                   w.hi,
                   count(v.take_secs) as cnt,
                   s.p50,
                   s.p95,
                   s.max_secs,
                   s.total
            from (values
                    (0, '<10s',   0,    10),
                    (1, '10-30s', 10,   30),
                    (2, '30-60s', 30,   60),
                    (3, '1-2m',   60,   120),
                    (4, '2-5m',   120,  300),
                    (5, '5-10m',  300,  600),
                    (6, '10-30m', 600,  1800),
                    (7, '30m-1h', 1800, 3600),
                    (8, '>=1h',   3600, 2147483647)
                 ) as w(idx, label, lo, hi)
            cross join stats s
            left join vals v on v.take_secs >= w.lo and v.take_secs < w.hi
            group by w.idx, w.label, w.lo, w.hi, s.p50, s.p95, s.max_secs, s.total
            order by w.idx
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, bizDate, days, bizDate);
        List<Map<String, Object>> buckets = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        if (rows.isEmpty())
        {
            result.put("buckets", buckets);
            return result;
        }
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("idx", row.get("idx"));
            bucket.put("label", row.get("label"));
            bucket.put("cnt", row.get("cnt"));
            buckets.add(bucket);
        }
        Map<String, Object> first = rows.get(0);
        result.put("buckets", buckets);
        result.put("p50", first.get("p50"));
        result.put("p95", first.get("p95"));
        result.put("max_secs", first.get("max_secs"));
        result.put("total", first.get("total"));
        return result;
    }
}
