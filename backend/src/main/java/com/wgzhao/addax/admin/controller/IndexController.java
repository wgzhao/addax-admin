package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.service.DashboardStatService;
import com.wgzhao.addax.admin.service.SourceService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TableService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监控主页面控制器，主要用于汇总信息和图表展示
 */
@RestController
@RequestMapping("/dashboard")
@AllArgsConstructor
public class IndexController
{
    /**
     * 统计服务
     */
    private final StatService statService;
    /**
     * 首页图表聚合统计服务
     */
    private final DashboardStatService dashboardStatService;
    /**
     * 数据源服务
     */
    private final SourceService sourceService;
    /**
     * 表服务
     */
    private final TableService tableService;

    /**
     * 获取各数据源采集完成率，用于图表展示
     *
     * @return 完成率列表
     */
    @RequestMapping("/accomplish-ratio")
    public ResponseEntity<List<Map<String, Object>>> accomplishRatio()
    {
        return ResponseEntity.ok(statService.statLastAccompliRatio());
    }

    /**
     * 获取最近5天采集耗时对比
     *
     * @return 耗时数据列表
     */
    @RequestMapping("/last-5d-collect-time")
    public ResponseEntity<List<Map<String, Object>>> last5DaysEtlTime()
    {
        return ResponseEntity.ok(statService.statLast5DaysTimeBySource());
    }

    /**
     * 获取最近 5 天的采集数据量对比
     *
     * @return 数据量列表
     */
    @RequestMapping("/last-5d-collect-data")
    public ResponseEntity<List<Map<String, Object>>> last5DaysEtlData()
    {
        return ResponseEntity.ok(statService.statLast5DaysDataBySource());
    }

    /**
     * 获取最近交易日采集的数据量（单位GB）
     *
     * @return 数据量
     */
    @RequestMapping("/last-collect-data")
    public ResponseEntity<Double> lastEtlData()
    {
        return ResponseEntity.ok(statService.statTotalData());
    }

    /**
     * 累计采集数据量（单位GiB）
     *
     * @return 累计数据量
     */
    @RequestMapping("/total-collect-data")
    public ResponseEntity<Double> totalEtlData()
    {
        return ResponseEntity.ok(statService.statAllTotalData());
    }

    /**
     * 累计采集天数（去重 biz_date）
     *
     * @return 累计天数
     */
    @RequestMapping("/total-collect-days")
    public ResponseEntity<Long> totalCollectDays()
    {
        return ResponseEntity.ok(statService.statTotalCollectDays());
    }

    /**
     * 最近采集周期（最新业务日期）全部任务耗时合计（秒）
     *
     * @return 耗时秒数
     */
    @RequestMapping("/total-collect-time")
    public ResponseEntity<Long> totalCollectTime()
    {
        return ResponseEntity.ok(statService.statTotalCollectTimeSecs());
    }

    /**
     * 首页统计卡片聚合数据：一次请求返回全部卡片所需指标
     *
     * @return 聚合数据
     */
    @RequestMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary()
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ratios", statService.statLastAccompliRatio());
        data.put("allDbSourceCount", sourceService.getAllSources());
        data.put("tableCount", tableService.getValidTableCount());
        data.put("allTableCount", tableService.getAllTableCount());
        data.putAll(statService.statEtlSummary());
        return ResponseEntity.ok(data);
    }

    /**
     * 获取最近12个月采集累计数据量（单位GiB）
     *
     * @return 月度数据量列表
     */
    @RequestMapping("/last-12m-collect-data")
    public ResponseEntity<?> last12MonthsData()
    {
        return ResponseEntity.ok(statService.statLast12MonthsData());
    }

    /**
     * 获取采集表数量
     *
     * @return 表数量
     */
    @RequestMapping("/collect-table-count")
    public ResponseEntity<Integer> tableCount()
    {
        return ResponseEntity.ok(tableService.getValidTableCount());
    }

    /**
     * 获取所有采集表数量
     *
     * @return 表数量
     */
    @RequestMapping("/all-collect-table-count")
    public ResponseEntity<Long> allTableCount()
    {
        return ResponseEntity.ok(tableService.getAllTableCount());
    }

    /**
     * 获取所有的采集源数量
     */
    @RequestMapping("/all-collect-source-count")
    public ResponseEntity<Long> allSourceCount()
    {
        return ResponseEntity.ok(sourceService.getAllSources());
    }

    /**
     * 获取数据源数量
     *
     * @return 数据源数量
     */
    @GetMapping("/collect-source-count")
    public ResponseEntity<Integer> sourceCount()
    {
        return ResponseEntity.ok(sourceService.getValidSources());
    }

    /**
     * 当前采集表状态分布（实时）
     *
     * @return 状态计数列表
     */
    @GetMapping("/table-status-dist")
    public ResponseEntity<List<Map<String, Object>>> tableStatusDist()
    {
        return ResponseEntity.ok(dashboardStatService.statTableStatusDistribution());
    }

    /**
     * 近 14 天按操作类型的失败趋势
     *
     * @return 每日每类型的失败次数
     */
    @GetMapping("/last-14d-fail-trend")
    public ResponseEntity<List<Map<String, Object>>> last14dFailTrend()
    {
        return ResponseEntity.ok(dashboardStatService.statLast14DaysFailTrend());
    }

    /**
     * 近 N 天失败次数最多的采集任务排行
     *
     * @param days 统计天数，默认 14
     * @param limit 返回条数，默认 10
     * @return 失败任务列表
     */
    @GetMapping("/fail-top-tables")
    public ResponseEntity<List<Map<String, Object>>> failTopTables(
        @RequestParam(defaultValue = "14") int days,
        @RequestParam(defaultValue = "10") int limit)
    {
        return ResponseEntity.ok(dashboardStatService.statTopFailTables(days, limit));
    }

    /**
     * 近 30 天每日错误行占比
     *
     * @return 每日错误行占比列表
     */
    @GetMapping("/last-30d-error-rate")
    public ResponseEntity<List<Map<String, Object>>> last30dErrorRate()
    {
        return ResponseEntity.ok(dashboardStatService.statLast30DaysErrorRate());
    }

    /**
     * 近 N 天采集缺失日历（每日应采/实采/缺失表数）
     *
     * @param days 统计天数，默认 56（8 周）
     * @return 每日应采/实采/缺失数据
     */
    @GetMapping("/missing-collect-calendar")
    public ResponseEntity<List<Map<String, Object>>> missingCollectCalendar(
        @RequestParam(defaultValue = "56") int days)
    {
        return ResponseEntity.ok(dashboardStatService.statMissingCollectCalendar(days));
    }

    /**
     * 近 12 个月每月记录数与数据量（GiB）
     *
     * @return 月度记录数与数据量列表
     */
    @GetMapping("/last-12m-recs-bytes")
    public ResponseEntity<List<Map<String, Object>>> last12mRecsBytes()
    {
        return ResponseEntity.ok(dashboardStatService.statLast12MonthsRecsAndBytes());
    }

    /**
     * 近 30 天各数据源采集量贡献（Top8 + 其他）
     *
     * @return 每日每源数据量长表
     */
    @GetMapping("/last-30d-source-contribution")
    public ResponseEntity<List<Map<String, Object>>> last30dSourceContribution()
    {
        return ResponseEntity.ok(dashboardStatService.statLast30DaysSourceContribution());
    }

    /**
     * 近 N 天平均耗时最长的采集任务排行
     *
     * @param days 统计天数，默认 30
     * @param limit 返回条数，默认 10
     * @return 慢表列表
     */
    @GetMapping("/slow-top-tables")
    public ResponseEntity<List<Map<String, Object>>> slowTopTables(
        @RequestParam(defaultValue = "30") int days,
        @RequestParam(defaultValue = "10") int limit)
    {
        return ResponseEntity.ok(dashboardStatService.statTopSlowTables(days, limit));
    }

    /**
     * 近 N 天采集耗时分布直方图及 P50/P95 分位数
     *
     * @param days 统计天数，默认 30
     * @return 分桶计数与分位数
     */
    @GetMapping("/take-secs-histogram")
    public ResponseEntity<Map<String, Object>> takeSecsHistogram(
        @RequestParam(defaultValue = "30") int days)
    {
        return ResponseEntity.ok(dashboardStatService.statTakeSecsHistogram(days));
    }
}
