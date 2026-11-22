package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.service.StatService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Aggregated collection data kept for the pages that replaced the retired 运行监控 view:
 * {@code /accomplish} feeds 首页 → 今日运行, and {@code /insight/*} feeds 数据治理 → 数据洞察.
 * The legacy blocks (special tasks, rejected rows, SMS history, system risks) were removed
 * with the page they belonged to.
 */
@RestController
@RequestMapping("/monitor")
@AllArgsConstructor
public class MonitorController
{

    private final StatService statService;

    /**
     * 获取最近两天采集完成情况列表
     *
     * @return 采集完成情况列表
     */
    @GetMapping("/accomplish")
    public ResponseEntity<List<Map<String, Object>>> getLast2DaysCompleteList()
    {
        return ResponseEntity.ok(statService.getLast2DaysCompleteList());
    }

    // 近 15 天内，数据量无变化的表
    @GetMapping("/insight/no-change")
    public ResponseEntity<List<Map<String, Object>>> insightNoChange(
        @RequestParam(value = "days", defaultValue = "15") int days)
    {
        return ResponseEntity.ok(statService.getNoTableRowsChangeListInDays(days));
    }

    // 近 15 天内，数据变化率小于 2% 的表
    @GetMapping("/insight/low-change")
    public ResponseEntity<List<Map<String, Object>>> insightLowChange(
        @RequestParam(value = "days", defaultValue = "15") int days,
        @RequestParam(value = "threshold", defaultValue = "2") double threshold)
    {
        return ResponseEntity.ok(statService.getLowTableRowsChangeRateList(days, threshold));
    }

    // 近 15 天内，数据变化率超过 40% 的表
    @GetMapping("/insight/high-change")
    public ResponseEntity<List<Map<String, Object>>> insightHighChange(
        @RequestParam(value = "days", defaultValue = "15") int days,
        @RequestParam(value = "threshold", defaultValue = "40") double threshold)
    {
        return ResponseEntity.ok(statService.getHighTableRowsChangeRateList(days, threshold));
    }

    // 近 15 天内，采集耗时变动率超过 40% 的表
    @GetMapping("/insight/time-change")
    public ResponseEntity<List<Map<String, Object>>> insightTimeChange(
        @RequestParam(value = "days", defaultValue = "15") int days,
        @RequestParam(value = "threshold", defaultValue = "40") double threshold)
    {
        return ResponseEntity.ok(statService.getHighTableTimeChangeRateList(days, threshold));
    }

    // 近 N 天内，缺失采集记录的有效表
    @GetMapping("/insight/missing-collect")
    public ResponseEntity<List<Map<String, Object>>> insightMissingCollect(
        @RequestParam(value = "days", defaultValue = "15") int days)
    {
        return ResponseEntity.ok(statService.getMissingCollectTablesInDays(days));
    }
}
