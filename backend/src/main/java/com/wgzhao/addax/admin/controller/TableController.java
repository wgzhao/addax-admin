package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.BatchTableStatusDto;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.service.ColumnService;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 采集表管理接口（RESTful规范），提供采集表的分页查询、详情、统计等功能
 */
@Tag(name = "采集表配置管理接口")
@RestController
@RequestMapping("/tables")
@AllArgsConstructor
public class TableController
{
    /** 采集表服务 */
    private final TableService tableService;
    /** 采集表数据仓库 */
    private final EtlTableRepo etlTableRepo;
    /** 统计服务 */
    private final StatService statService;
    /** 列服务 */
    private final ColumnService columnService;
    /** 作业内容服务 */
    private final JobContentService jobContentService;

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
    public ResponseEntity<Page<VwEtlTableWithSource>> listTables(
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "每页记录数") @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @Parameter(description = "查询关键字") @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "排序字段") @RequestParam(value = "sortField", required = false) String sortField,
            @Parameter(description = "排序顺序") @RequestParam(value = "sortOrder", required = false) String sortOrder)
    {
        if (page < 0)
            page = 0;
        if (pageSize == -1)
            pageSize = Integer.MAX_VALUE;
        Page<VwEtlTableWithSource> result;
        if (status != null && !status.isEmpty()) {
            result = tableService.getVwTablesByStatus(page, pageSize, q, status, sortField, sortOrder);
        }
        else {
            result = tableService.getVwTablesInfo(page, pageSize, q, sortField, sortOrder);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 查询单个采集表
     * @param tableId 采集表ID
     * @return 采集表详情
     */
    @Operation(summary = "查询单个采集表")
    @GetMapping("/{tableId}")
    public ResponseEntity<VwEtlTableWithSource> getTable(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        VwEtlTableWithSource table = tableService.findOneTableInfo(tableId);
        if (table == null)
            throw new ApiException(404, "Table not found");
        return ResponseEntity.ok(table);
    }

    /**
     * 删除采集表
     * @param tableId 采集表ID
     * @return 删除结果提示
     */
    @Operation(summary = "删除采集表")
    @DeleteMapping("/{tableId}")
    public ResponseEntity<String> deleteTable(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        if (!etlTableRepo.existsById(tableId))
            throw new ApiException(404, "Table not found");
        tableService.deleteTable(tableId);
        return ResponseEntity.ok("表以及相关资源删除成功");
    }

    /**
     * 更新采集表
     * @param tableId 采集表ID
     * @param etl 更新的采集表数据
     * @return 更新后的采集表
     */
    @Operation(summary = "更新采集表")
    @PutMapping("/{tableId}")
    public ResponseEntity<EtlTable> updateTable(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId,
                                                @RequestBody EtlTable etl)
    {
        if (etl.getId() == null || etl.getId() != tableId) {
            throw new ApiException(400, "Table ID in path and body must match");
        }
        if (!etlTableRepo.existsById(tableId)) {
            throw new ApiException(404, "Table not found");
        }
        EtlTable updated = etlTableRepo.save(etl);
        return ResponseEntity.ok(updated);
    }

    /**
     * 查询表字段
     * @param tableId 采集表ID
     * @return 采集表字段列表
     */
    @Operation(summary = "查询表字段")
    @GetMapping("/{tableId}/columns")
    public ResponseEntity<List<EtlColumn>> getTableColumns(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        return ResponseEntity.ok(columnService.getColumns(tableId));
    }

    /**
     * 查询表采集统计
     * @param tableId 采集表ID
     * @return 采集统计信息
     */
    @Operation(summary = "查询表采集统计")
    @GetMapping("/{tableId}/statistics")
    public ResponseEntity<List<EtlStatistic>> getTableStatistics(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        return ResponseEntity.ok(statService.getLast15Records(tableId));
    }

    /**
     * 批量保存采集表
     * @param etls 采集表列表
     * @return 保存的采集表数量
     */
    @Operation(summary = "批量保存采集表")
    @PostMapping("/batch")
    public ResponseEntity<Integer> saveBatchTables(@RequestBody List<EtlTable> etls)
    {
        List<EtlTable> saveTables = tableService.batchCreateTable(etls);
        // 异步刷新资源
        for (EtlTable table : saveTables) {
            tableService.refreshTableResourcesAsync(table);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(etls.size());
    }

    /**
     * 新增单个采集表
     * @param etl 采集表数据
     * @return 新增的采集表
     */
    @Operation(summary = "新增单个采集表")
    @PostMapping("")
    public ResponseEntity<EtlTable> saveTable(@RequestBody EtlTable etl)
    {
        EtlTable saved = tableService.createTable(etl);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 刷新所有表的关联资源
     * 触发一个异步任务，用于更新所有表的元数据（字段）和采集任务文件
     * @param mode 刷新模式
     * @return 无内容响应
     */
    @Operation(summary = "刷新所有表的关联资源", description = "触发一个异步任务，用于更新所有表的元数据（字段）和采集任务文件")
    @PostMapping("/actions/refresh")
    public ResponseEntity<Void> refreshAllTableResources(@RequestParam(value = "mode", defaultValue = "need") String mode)
    {
        CompletableFuture.runAsync(tableService::refreshAllTableResources);
        return ResponseEntity.accepted().build();
    }

    /**
     * 刷新表关联资源
     * 触发一个异步任务，用于更新指定表的元数据（字段）和采集任务文件
     * @param tableId 采集表ID
     * @return 任务结果
     */
    @Operation(summary = "刷新表关联资源", description = "触发一个异步任务，用于更新指定表的元数据（字段）和采集任务文件")
    @PostMapping("/{tableId}/actions/refresh")
    public ResponseEntity<TaskResultDto> refreshTableResources(
            @Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        if (!etlTableRepo.existsById(tableId)) {
            return ResponseEntity.status(400).body(TaskResultDto.failure("tableId 对应的采集表不存在", 0));
        }
        TaskResultDto taskResultDto = tableService.refreshTableResources(tableId);
        if (taskResultDto.success()) {
            return ResponseEntity.ok(taskResultDto);
        }
        else {
            return ResponseEntity.internalServerError().body(taskResultDto);
        }
    }

    /**
     * 批量更新表状态
     * @param params 表状态更新参数，类似如下:
     *  "tids":[32,34,33,36,35],"status":"N","retryCnt":3}
     * @return 更新的表数量
     */
    @Operation(summary = "批量更新表状态")
    @PostMapping("/batch/status")
    public ResponseEntity<Integer> batchUpdateStatus(@RequestBody BatchTableStatusDto params)
    {
        // 具体实现略
        tableService.updateTableStatuses(params);
        return ResponseEntity.ok(1);
    }

    /**
     * 查询表视图
     * @param params 查询参数
     * @return 表视图列表
     */
    @Operation(summary = "查询表视图")
    @GetMapping("/view")
    public ResponseEntity<List<VwEtlTableWithSource>> listTableViews(@RequestParam Map<String, String> params)
    {
        // 具体实现略
        return ResponseEntity.ok(new ArrayList<>());
    }

    /**
     * 获取Addax Job模板
     * @param tableId 采集表ID
     * @return Addax Job模板内容
     */
    @Operation(summary = "获取Addax Job模板")
    @GetMapping("/{tableId}/addax-job")
    public ResponseEntity<String> getAddaxJob(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        // 具体实现略
        String job = jobContentService.getJobContent(tableId);
        if (job == null || job.isEmpty()) {
           return ResponseEntity.badRequest().body("No job content found for the given table ID");
        }
        else {
            return ResponseEntity.ok(job);
        }
    }
}

//    // 对单个表执行采集任务
//    @Operation(summary = "执行采集任务", description = "根据采集表ID立即执行单个采集任务")
//    @PostMapping("/{tableId}/actions/collect")
//    public ResponseEntity<Map<String, Object>> executeTableTask(
//            @Parameter(description = "采集表ID") @PathVariable("tableId") long tableId) {
//        EtlTable etlTable = tableService.getTable(tableId);
//        if (etlTable == null) {
//            throw new ApiException(404, "Table not found");
//        }
