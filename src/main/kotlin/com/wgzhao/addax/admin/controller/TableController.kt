package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.exception.ApiException
import com.wgzhao.addax.admin.model.EtlColumn
import com.wgzhao.addax.admin.model.EtlStatistic
import com.wgzhao.addax.admin.model.EtlTable
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.repository.EtlTableRepo
import com.wgzhao.addax.admin.service.ColumnService
import com.wgzhao.addax.admin.service.JobContentService
import com.wgzhao.addax.admin.service.StatService
import com.wgzhao.addax.admin.service.TableService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 采集表管理接口（RESTful规范），提供采集表的分页查询、详情、统计等功能
 */
@Tag(name = "采集表配置管理接口")
@RestController
@RequestMapping("/tables")
class TableController(
    private val tableService: TableService,
    private val etlTableRepo: EtlTableRepo,
    private val statService: StatService,
    private val columnService: ColumnService,
    private val jobContentService: JobContentService
) {

    /**
     * 分页查询采集表
     * @param page 页码
     * @param pageSize 每页记录数
     * @param q 查询关键字
     * @param status 状态
     * @param sortField 排序字段
     * @param sortOrder 排序顺序
     * @return 采集表分页结果
     */
    @Operation(summary = "分页查询采集表")
    @GetMapping("")
    fun listTables(
        @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "0") page: Int,
        @Parameter(description = "每页记录数") @RequestParam(value = "pageSize", defaultValue = "10") pageSize: Int,
        @Parameter(description = "查询关键字") @RequestParam(value = "q", required = false) q: String?,
        @Parameter(description = "状态") @RequestParam(value = "status", required = false) status: String?,
        @Parameter(description = "排序字段") @RequestParam(value = "sortField", required = false) sortField: String?,
        @Parameter(description = "排序顺序") @RequestParam(value = "sortOrder", required = false) sortOrder: String?
    ): ResponseEntity<Page<VwEtlTableWithSource?>?> {
        val safePage = page.coerceAtLeast(0)
        val safePageSize = if (pageSize == -1) Int.MAX_VALUE else pageSize
        val result = if (!status.isNullOrEmpty()) {
            tableService.getVwTablesByStatus(safePage, safePageSize, q, status, sortField, sortOrder)
        } else {
            tableService.getVwTablesInfo(safePage, safePageSize, q, sortField, sortOrder)
        }
        return ResponseEntity.ok(result)
    }

    /**
     * 查询单个采集表
     * @param tableId 采集表ID
     * @return 采集表详情
     */
    @Operation(summary = "查询单个采集表")
    @GetMapping("/{tableId}")
    fun getTable(@Parameter(description = "采集表ID") @PathVariable tableId: Long): ResponseEntity<VwEtlTableWithSource> {
        val table = tableService.findOneTableInfo(tableId)
            ?: throw ApiException(404, "Table not found")
        return ResponseEntity.ok(table)
    }

    /**
     * 删除采集表
     * @param tableId 采集表ID
     * @return 删除结果提示
     */
    @Operation(summary = "删除采集表")
    @DeleteMapping("/{tableId}")
    fun deleteTable(@Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long): ResponseEntity<String?> {
        if (!etlTableRepo.existsById(tableId)) throw ApiException(404, "Table not found")
        tableService.deleteTable(tableId)
        return ResponseEntity.ok<String?>("表以及相关资源删除成功")
    }

    /**
     * 更新采集表
     * @param tableId 采集表ID
     * @param etl 更新的采集表数据
     * @return 更新后的采集表
     */
    @Operation(summary = "更新采集表")
    @PutMapping("/{tableId}")
    fun updateTable(
        @Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long,
        @RequestBody etl: EtlTable
    ): ResponseEntity<EtlTable?> {
        if (etl.id == null || etl.id != tableId) {
            throw ApiException(400, "Table ID in path and body must match")
        }
        if (!etlTableRepo.existsById(tableId)) {
            throw ApiException(404, "Table not found")
        }
        val updated = etlTableRepo.save<EtlTable>(etl)
        return ResponseEntity.ok<EtlTable?>(updated)
    }

    /**
     * 查询表字段
     * @param tableId 采集表ID
     * @return 采集表字段列表
     */
    @Operation(summary = "查询表字段")
    @GetMapping("/{tableId}/columns")
    fun getTableColumns(@Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long): ResponseEntity<List<EtlColumn?>?> {
        return ResponseEntity.ok<List<EtlColumn?>?>(columnService.getColumns(tableId))
    }

    /**
     * 查询表采集统计
     * @param tableId 采集表ID
     * @return 采集统计信息
     */
    @Operation(summary = "查询表采集统计")
    @GetMapping("/{tableId}/statistics")
    fun getTableStatistics(@Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long): ResponseEntity<List<EtlStatistic?>?> {
        return ResponseEntity.ok<List<EtlStatistic?>?>(statService.getLast15Records(tableId))
    }

    /**
     * 批量保存采集表
     * @param etls 采集表列表
     * @return 保存的采集表数量
     */
    @Operation(summary = "批量保存采集表")
    @PostMapping("/batch")
    fun saveBatchTables(@RequestBody etls: MutableList<EtlTable?>): ResponseEntity<Int?> {
        val saveTables = tableService.batchCreateTable(etls)
        // 异步刷新资源
        for (table in saveTables) {
            tableService.refreshTableResourcesAsync(table)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body<Int?>(etls.size)
    }

    /**
     * 新增单个采集表
     * @param etl 采集表数据
     * @return 新增的采集表
     */
    @Operation(summary = "新增单个采集表")
    @PostMapping("")
    fun saveTable(@RequestBody etl: EtlTable): ResponseEntity<EtlTable?> {
        val saved = tableService.createTable(etl)
        return ResponseEntity.status(HttpStatus.CREATED).body<EtlTable?>(saved)
    }

    /**
     * 刷新所有表的关联资源
     * 触发一个异步任务，用于更新所有表的元数据（字段）和采集任务文件
     * @param mode 刷新模式
     * @return 无内容响应
     */
    @Operation(summary = "刷新所有表的关联资源", description = "触发一个异步任务，用于更新所有表的元数据（字段）和采集任务文件")
    @PostMapping("/actions/refresh")
    fun refreshAllTableResources(@RequestParam(value = "mode", defaultValue = "need") mode: String?): ResponseEntity<Void?> {
        tableService.refreshAllTableResources();
        return ResponseEntity.accepted().build<Void?>()
    }

    /**
     * 刷新表关联资源
     * 触发一个异步任务，用于更新指定表的元数据（字段）和采集任务文件
     * @param tableId 采集表ID
     * @return 任务结果
     */
    @Operation(summary = "刷新表关联资源", description = "触发一个异步任务，用于更新指定表的元数据（字段）和采集任务文件")
    @PostMapping("/{tableId}/actions/refresh")
    fun refreshTableResources(
        @Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long
    ): ResponseEntity<TaskResultDto?> {
        if (!etlTableRepo.existsById(tableId)) {
            return ResponseEntity.status(400).body<TaskResultDto?>(failure("tableId 对应的采集表不存在", 0))
        }
        val taskResultDto = tableService.refreshTableResources(tableId)
        return if (taskResultDto.success) {
            ResponseEntity.ok<TaskResultDto?>(taskResultDto)
        } else {
            ResponseEntity.internalServerError().body<TaskResultDto?>(taskResultDto)
        }
    }

    /**
     * 批量更新表状态
     * @param params 表状态更新参数
     * @return 更新的表数量
     */
    @Operation(summary = "批量更新表状态")
    @PostMapping("/batch/status")
    fun batchUpdateStatus(@RequestBody params: MutableMap<String?, Any?>?): ResponseEntity<Int?> {
        // 具体实现略
        return ResponseEntity.ok<Int?>(1)
    }

    /**
     * 查询表视图
     * @param params 查询参数
     * @return 表视图列表
     */
    @Operation(summary = "查询表视图")
    @GetMapping("/view")
    fun listTableViews(@RequestParam params: MutableMap<String?, String?>?): ResponseEntity<MutableList<VwEtlTableWithSource?>?> {
        // 具体实现略
        return ResponseEntity.ok<MutableList<VwEtlTableWithSource?>?>(ArrayList<VwEtlTableWithSource?>())
    }

    /**
     * 获取Addax Job模板
     * @param tableId 采集表ID
     * @return Addax Job模板内容
     */
    @Operation(summary = "获取Addax Job模板")
    @GetMapping("/{tableId}/addax-job")
    fun getAddaxJob(@Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long): ResponseEntity<String?> {
        // 具体实现略
        val job: String? = jobContentService.getJobContent(tableId)
        return ResponseEntity.badRequest().body<String>("No job content found for the given table ID")
    }
} //    // 对单个表执行采集任务
//    @Operation(summary = "执行采集任务", description = "根据采集表ID立即执行单个采集任务")
//    @PostMapping("/{tableId}/actions/collect")
//    public ResponseEntity<Map<String, Object>> executeTableTask(
//            @Parameter(description = "采集表ID") @PathVariable("tableId") long tableId) {
//        EtlTable etlTable = tableService.getTable(tableId);
//        if (etlTable == null) {
//            throw new ApiException(404, "Table not found");
//        }

