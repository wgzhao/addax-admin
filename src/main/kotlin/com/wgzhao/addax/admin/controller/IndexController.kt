package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.service.SourceService
import com.wgzhao.addax.admin.service.StatService
import com.wgzhao.addax.admin.service.TableService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 监控主页面控制器，主要用于汇总信息和图表展示
 */
@RestController
@RequestMapping("/dashboard")
class IndexController(
    private val statService: StatService,
    private val sourceService: SourceService,
    private val tableService: TableService
) {
    /**
     * 获取各数据源采集完成率，用于图表展示
     * @return 完成率列表
     */
    @GetMapping("/accomplish-ratio")
    fun accomplishRatio(): ResponseEntity<List<Map<String, Any>>> =
        ResponseEntity.ok(statService.statLastAccompRatio())

    /**
     * 获取最近5天采集耗时对比
     * @return 耗时数据列表
     */
    @GetMapping("/last-5d-collect-time")
    fun last5DaysEtlTime(): ResponseEntity<List<Map<String, Any>>> =
        ResponseEntity.ok(statService.statLast5DaysTimeBySource())

    /**
     * 获取最近交易日采集的数据量（单位GB）
     * @return 数据量
     */
    @GetMapping("/last-collect-data")
    fun lastEtlData(): ResponseEntity<Double> =
        ResponseEntity.ok(statService.statTotalData())

    /**
     * 获取最近12个月采集累计数据量（单位GiB）
     * @return 月度数据量列表
     */
    @GetMapping("/last-12m-collect-data")
    fun last12MonthsData(): ResponseEntity<List<Map<String, Any>>> =
        ResponseEntity.ok(statService.statLast12MonthsData())

    /**
     * 获取采集表数量
     * @return 表数量
     */
    @GetMapping("/collect-table-count")
    fun tableCount(): ResponseEntity<Int> =
        ResponseEntity.ok(tableService.validTableCount)

    /**
     * 获取数据源数量
     * @return 数据源数量
     */
    @GetMapping("/collect-source-count")
    fun sourceCount(): ResponseEntity<Int> =
        ResponseEntity.ok(sourceService.validSources)
}
