package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.model.EtlTableStatus;
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
 * 采集表管理接口（RESTful 规范）
 */
@Tag(name = "采集表配置管理接口")
@RestController
@RequestMapping("/tables")
@AllArgsConstructor
public class TableController
{

    private final TableService tableService;
    private final EtlTableRepo etlTableRepo;
    private final StatService statService;
    private final ColumnService columnService;
    private final JobContentService jobContentService;

    // 分页查询采集表
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
            EtlTableStatus statusEnum = null;
            try {
                statusEnum = EtlTableStatus.valueOf(status);
            } catch (Exception ignored) {}
            result = tableService.getVwTablesByStatus(page, pageSize, q, statusEnum, sortField, sortOrder);
        }
        else {
            result = tableService.getVwTablesInfo(page, pageSize, q, sortField, sortOrder);
        }
        return ResponseEntity.ok(result);
    }

    // 查询单个采集表
    @Operation(summary = "查询单个采集表")
    @GetMapping("/{tableId}")
    public ResponseEntity<VwEtlTableWithSource> getTable(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        VwEtlTableWithSource table = tableService.findOneTableInfo(tableId);
        if (table == null)
            throw new ApiException(404, "Table not found");
        return ResponseEntity.ok(table);
    }

    // 删除采集表
    @Operation(summary = "删除采集表")
    @DeleteMapping("/{tableId}")
    public ResponseEntity<String> deleteTable(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        if (!etlTableRepo.existsById(tableId))
            throw new ApiException(404, "Table not found");
        if (tableService.deleteTable(tableId)) {
            return ResponseEntity.ok("表以及相关资源删除成功");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("表删除失败");
        }
    }

    // 查询表字段
    @Operation(summary = "查询表字段")
    @GetMapping("/{tableId}/columns")
    public ResponseEntity<List<EtlColumn>> getTableColumns(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        return ResponseEntity.ok(columnService.getColumns(tableId));
    }

    // 查询表采集统计
    @Operation(summary = "查询表采集统计")
    @GetMapping("/{tableId}/statistics")
    public ResponseEntity<List<EtlStatistic>> getTableStatistics(@Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        return ResponseEntity.ok(statService.getLast15Records(tableId));
    }

    // 批量保存采集表
    @Operation(summary = "批量保存采集表")
    @PostMapping("/batch")
    public ResponseEntity<Integer> batchCreateTables(@RequestBody List<EtlTable> tables)
    {
        tableService.batchCreateTable(tables);
        return ResponseEntity.status(HttpStatus.CREATED).body(tables.size());
    }

    // 新增单个采集表
    @Operation(summary = "新增单个采集表")
    @PostMapping("")
    public ResponseEntity<EtlTable> addTable(@RequestBody EtlTable etl)
    {
        EtlTable saved = tableService.create(etl);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "刷新所有表的关联资源", description = "触发一个异步任务，用于更新所有表的元数据（字段）和采集任务文件")
    @PostMapping("/actions/refresh")
    public ResponseEntity<Void> refreshAllTableResources(@RequestParam(value = "mode", defaultValue = "need") String mode)
    {
        CompletableFuture.runAsync(tableService::refreshAllTableResources);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "刷新表关联资源", description = "触发一个异步任务，用于更新指定表的元数据（字段）和采集任务文件")
    @PostMapping("/{tableId}/actions/refresh")
    public ResponseEntity<TaskResultDto> refreshTableResources(
            @Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        if (!etlTableRepo.existsById(tableId)) {
            return ResponseEntity.status(400).body(TaskResultDto.failure("tableId 对应的采集表不存在", 0));
        }
        TaskResultDto taskResultDto = tableService.refreshTableResources(tableId);
        if (taskResultDto.isSuccess()) {
            return ResponseEntity.ok(taskResultDto);
        }
        else {
            return ResponseEntity.internalServerError().body(taskResultDto);
        }
    }

    // 批量更新表状态
    @Operation(summary = "批量更新表状态")
    @PostMapping("/batch/status")
    public ResponseEntity<Integer> batchUpdateStatus(@RequestBody Map<String, Object> params)
    {
        List<Integer> idList = (List<Integer>) params.get("ids");
        String statusStr = (String) params.get("status");
        Integer retryCnt = params.get("retryCnt") != null ? (Integer) params.get("retryCnt") : 3;
        if (idList == null || statusStr == null) {
            return ResponseEntity.badRequest().body(0);
        }
        EtlTableStatus statusEnum;
        try {
            statusEnum = EtlTableStatus.valueOf(statusStr);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(0);
        }
        List<Long> ids = idList.stream().map(Integer::longValue).toList();
        tableService.batchUpdateStatusAndFlag(ids, statusEnum, retryCnt);
        return ResponseEntity.ok(ids.size());
    }

    // 查询表视图
    @Operation(summary = "查询表视图")
    @GetMapping("/view")
    public ResponseEntity<List<VwEtlTableWithSource>> listTableViews(@RequestParam Map<String, String> params)
    {
        // 具体实现略
        return ResponseEntity.ok(new ArrayList<>());
    }

    // 获取Addax Job模板
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
