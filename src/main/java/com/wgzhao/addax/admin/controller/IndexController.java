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
 * 监控主页面，主要是汇总信息和图表展示
 */
@RestController
@RequestMapping("/dashboard")
@AllArgsConstructor
public class IndexController
{

    private final StatService statService;
    private final SourceService sourceService;
    private final TableService tableService;

    // 各数据源采集完成率，用于图表展示
    @RequestMapping("/accomplish-ratio")
    public ResponseEntity<List<Map<String, Object>>> accomplishRatio()
    {
        return ResponseEntity.ok(statService.statLastAccompRatio());
    }

    //  最近5天采集耗时对比
    @RequestMapping("/last-5d-collect-time")
    public ResponseEntity<List<Map<String, Object>>> last5DaysEtlTime()
    {
        return ResponseEntity.ok(statService.statLast5DaysTimeBySource());
    }

    // 最近交易日采集的数据量， 以 GB 为单位
    @RequestMapping("/last-collect-data")
    public ResponseEntity<Double> lastEtlData()
    {
        return ResponseEntity.ok(statService.statTotalData());
    }

    // 获取最近12个月的采集累计数据量，单位为 GiB
    @RequestMapping("/last-12m-collect-data")
    public ResponseEntity<?> last12MonthsData()
    {
        // Adjusted method signature for consistency
        return ResponseEntity.ok(statService.statLast12MonthsData());
    }

    @RequestMapping("/collect-table-count")
    public ResponseEntity<Integer> tableCount()
    {
        return ResponseEntity.ok(tableService.getValidTableCount());
    }

    @GetMapping("/collect-source-count")
    public ResponseEntity<Integer> sourceCount()
    {
        return ResponseEntity.ok(sourceService.getValidSources());
    }
}
