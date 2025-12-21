package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.service.SourceService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TableService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
