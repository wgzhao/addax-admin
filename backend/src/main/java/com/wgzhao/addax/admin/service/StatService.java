package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.repository.EtlStatisticRepo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class StatService
{

    private final JdbcTemplate jdbcTemplate;
    private final EtlStatisticRepo etlStatisticRepo;
    private final SystemConfigService configService;

    // 最近一次采集的总数据量，单位 GB
    public Double statTotalData()
    {
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
    public List<Map<String, Object>> statLast12MonthsData()
    {
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

    // 按照采集来源统计最近 5 天的耗时，用来形成柱状图表
    public List<Map<String, Object>> statLast5DaysTimeBySource()
    {
        return etlStatisticRepo.findLast5DaysTakeTimes();
    }

    // 最近采集的完成率
    public List<Map<String, Object>> statLastAccompliRatio()
    {
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

    public boolean saveOrUpdate(EtlStatistic statistic)
    {
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

    public List<EtlStatistic> findErrorTask()
    {
        return etlStatisticRepo.findErrorTask();
    }

    // 根据采集表 ID 获取最近 15 条采集日志
    public List<EtlStatistic> getLast15Records(long tid)
    {
        return etlStatisticRepo.findTop15ByTidOrderByBizDateDesc(tid);
    }

    /**
     * 已数据源为单位，统计最近两天的采集完成情况
     *
     * @return List
     */
    public List<Map<String, Object>> getLast2DaysCompleteList()
    {
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
            to_char(b.y_begin_at, 'YYYY-mm-dd HH24:MI:SS') as y_begin_at,
            to_char(b.y_finish_at, 'YYYY-mm-dd HH24:MI:SS') as y_finish_at,
            b.y_take_secs,
            to_char(b.t_begin_at, 'YYYY-mm-dd HH24:MI:SS') as t_begin_at,
            to_char(b.t_finish_at, 'YYYY-mm-dd HH24:MI:SS') as t_finish_at,
            b.t_take_secs
            from total_info a
            join last2_info b
            on a.code = b.code
            order by a.start_at
            """;
        return jdbcTemplate.queryForList(sql, configService.getBizDateAsDate().plusDays(-1),
            configService.getBizDateAsDate());
    }

    public Double statAllTotalData()
    {
        String sql = """
            select
               round(sum(total_bytes)/1024/1024/1024,2) as total_gb
            from etl_statistic
            """;
        return jdbcTemplate.queryForObject(sql, Double.class);
    }

    public List<Map<String, Object>> statLast5DaysDataBySource()
    {
        return etlStatisticRepo.findLast5DaysDataMB();
    }

    public List<Map<String, Object>> getNoTableRowsChangeList(int days)
    {
        String sql = """
            WITH latest_per_day AS (
              SELECT DISTINCT ON (tid, biz_date)
                tid,
                biz_date,
                total_recs
              FROM etl_statistic
              where biz_date >  current_date - ?
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

    // 近 N 天内，数据量无变化的表（基于每日最新记录）
    public List<Map<String, Object>> getNoTableRowsChangeListInDays(int days)
    {
        int offset = Math.max(days - 1, 0);
        String sql = """
            WITH daily AS (
              SELECT DISTINCT ON (tid, biz_date)
                tid,
                biz_date,
                total_recs,
                take_secs
              FROM etl_statistic
              WHERE biz_date >= current_date - ?
              ORDER BY tid, biz_date DESC, start_at DESC, id DESC
            ),
            agg AS (
              SELECT
                tid,
                min(biz_date) as start_date,
                max(biz_date) as end_date,
                count(*) as day_count,
                min(total_recs) as min_recs,
                max(total_recs) as max_recs
              FROM daily
              GROUP BY tid
            )
            SELECT a.tid,
                   t.source_db,
                   t.source_table,
                   t.target_db,
                   t.target_table,
                   a.start_date,
                   a.end_date,
                   a.day_count,
                   a.min_recs as total_recs
            FROM agg a
            JOIN etl_table t
              ON a.tid = t.id
            WHERE t.status <> 'X'
              AND a.day_count >= 2
              AND a.max_recs = a.min_recs
            ORDER BY a.max_recs DESC
            """;
        return jdbcTemplate.queryForList(sql, offset);
    }

    // 近 N 天内，数据变化率小于指定阈值的表（基于每日最新记录）
    public List<Map<String, Object>> getLowTableRowsChangeRateList(int days, double thresholdPct)
    {
        int offset = Math.max(days - 1, 0);
        String sql = """
            WITH daily AS (
              SELECT DISTINCT ON (tid, biz_date)
                tid,
                biz_date,
                total_recs
              FROM etl_statistic
              WHERE biz_date >= current_date - ?
              ORDER BY tid, biz_date DESC, start_at DESC, id DESC
            ),
            agg AS (
              SELECT
                tid,
                min(biz_date) as start_date,
                max(biz_date) as end_date,
                count(*) as day_count,
                min(total_recs) as min_recs,
                max(total_recs) as max_recs
              FROM daily
              GROUP BY tid
            )
            SELECT a.tid,
                   t.source_db,
                   t.source_table,
                   t.target_db,
                   t.target_table,
                   a.start_date,
                   a.end_date,
                   a.day_count,
                   a.min_recs,
                   a.max_recs,
                   round(abs(a.max_recs - a.min_recs) * 100.0 / nullif(a.min_recs, 0), 2) as change_rate_pct
            FROM agg a
            JOIN etl_table t
              ON a.tid = t.id
            WHERE t.status <> 'X'
              AND a.day_count >= 2
              AND a.min_recs > 0
              AND round(abs(a.max_recs - a.min_recs) * 100.0 / nullif(a.min_recs, 0), 2) > 0
              AND round(abs(a.max_recs - a.min_recs) * 100.0 / nullif(a.min_recs, 0), 2) < ?
            ORDER BY change_rate_pct ASC, a.max_recs DESC
            """;
        return jdbcTemplate.queryForList(sql, offset, thresholdPct);
    }

    // 近 N 天内，数据变化率超过指定阈值的表（基于每日最新记录）
    public List<Map<String, Object>> getHighTableRowsChangeRateList(int days, double thresholdPct)
    {
        int offset = Math.max(days - 1, 0);
        String sql = """
            WITH daily AS (
              SELECT DISTINCT ON (tid, biz_date)
                tid,
                biz_date,
                total_recs
              FROM etl_statistic
              WHERE biz_date >= current_date - ?
              ORDER BY tid, biz_date DESC, start_at DESC, id DESC
            ),
            agg AS (
              SELECT
                tid,
                min(biz_date) as start_date,
                max(biz_date) as end_date,
                count(*) as day_count,
                min(total_recs) as min_recs,
                max(total_recs) as max_recs
              FROM daily
              GROUP BY tid
            )
            SELECT a.tid,
                   t.source_db,
                   t.source_table,
                   t.target_db,
                   t.target_table,
                   a.start_date,
                   a.end_date,
                   a.day_count,
                   a.min_recs,
                   a.max_recs,
                   round(abs(a.max_recs - a.min_recs) * 100.0 / nullif(a.min_recs, 0), 2) as change_rate_pct
            FROM agg a
            JOIN etl_table t
              ON a.tid = t.id
            WHERE t.status <> 'X'
              AND a.day_count >= 2
              AND a.min_recs > 0
              AND round(abs(a.max_recs - a.min_recs) * 100.0 / nullif(a.min_recs, 0), 2) > ?
            ORDER BY change_rate_pct DESC, a.max_recs DESC
            """;
        return jdbcTemplate.queryForList(sql, offset, thresholdPct);
    }

    // 近 N 天内，采集耗时变动率超过指定阈值的表（基于每日最新记录）
    public List<Map<String, Object>> getHighTableTimeChangeRateList(int days, double thresholdPct)
    {
        int offset = Math.max(days - 1, 0);
        String sql = """
            WITH daily AS (
              SELECT DISTINCT ON (tid, biz_date)
                tid,
                biz_date,
                take_secs
              FROM etl_statistic
              WHERE biz_date >= current_date - ?
              ORDER BY tid, biz_date DESC, start_at DESC, id DESC
            ),
            agg AS (
              SELECT
                tid,
                min(biz_date) as start_date,
                max(biz_date) as end_date,
                count(*) as day_count,
                min(take_secs) as min_secs,
                max(take_secs) as max_secs
              FROM daily
              GROUP BY tid
            )
            SELECT a.tid,
                   t.source_db,
                   t.source_table,
                   t.target_db,
                   t.target_table,
                   a.start_date,
                   a.end_date,
                   a.day_count,
                   a.min_secs,
                   a.max_secs,
                   round(abs(a.max_secs - a.min_secs) * 100.0 / nullif(a.min_secs, 0), 2) as change_rate_pct
            FROM agg a
            JOIN etl_table t
              ON a.tid = t.id
            WHERE t.status <> 'X'
              AND a.day_count >= 2
              AND a.min_secs > 0
              AND round(abs(a.max_secs - a.min_secs) * 100.0 / nullif(a.min_secs, 0), 2) > ?
            ORDER BY change_rate_pct DESC, a.max_secs DESC
            """;
        return jdbcTemplate.queryForList(sql, offset, thresholdPct);
    }

    // 获取指定表 ID 的最后一次采集时间
    public LocalDate getLastEtlDateByTid(long tid)
    {
        return etlStatisticRepo.findTop1ByTidOrderByBizDateDesc(tid).map(EtlStatistic::getBizDate).orElse(null);
    }
}
