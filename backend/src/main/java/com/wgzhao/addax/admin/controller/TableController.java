package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.BatchTableStatusDto;
import com.wgzhao.addax.admin.dto.PageResponse;
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
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采集表管理接口（RESTful规范），提供采集表的分页查询、详情、统计等功能
 */
@RestController
@RequestMapping("/tables")
@AllArgsConstructor
public class TableController
{
    /**
     * 采集表服务
     */
    private final TableService tableService;
    /**
     * 采集表数据仓库
     */
    private final EtlTableRepo etlTableRepo;
    /**
     * 统计服务
     */
    private final StatService statService;
    /**
     * 列服务
     */
    private final ColumnService columnService;
    /**
     * 作业内容服务
     */
    private final JobContentService jobContentService;

    /**
     * 分页查询采集表
     *
     * @param page 页码（查询参数），默认 0
     * @param pageSize 每页记录数（查询参数），默认 10，-1 表示不分页
     * @param q 查询关键字（查询参数），可选
     * @param status 状态（查询参数），可选
     * @param sortField 排序字段（查询参数），可选
     * @param sortOrder 排序顺序（查询参数），可选
     * @return 采集表分页结果
     */
    @GetMapping("")
    public ResponseEntity<PageResponse<VwEtlTableWithSource>> listTables(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "sourceId", required = false) Integer sourceId,
        @RequestParam(value = "sortField", required = false) String sortField,
        @RequestParam(value = "sortOrder", required = false) String sortOrder)
    {
        if (page < 0) {
            page = 0;
        }
        if (pageSize == -1) {
            pageSize = Integer.MAX_VALUE;
        }
        Page<VwEtlTableWithSource> result;
        if (status != null && !status.isEmpty()) {
            result = tableService.getVwTablesByStatus(page, pageSize, q, status, sourceId, sortField, sortOrder);
        }
        else {
            result = tableService.getVwTablesInfo(page, pageSize, q, sourceId, sortField, sortOrder);
        }
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /**
     * 查询单个采集表
     *
     * @param tableId 采集表ID（路径参数）
     * @return 采集表详情
     */
    @GetMapping("/{tableId}")
    public ResponseEntity<VwEtlTableWithSource> getTable(@PathVariable("tableId") long tableId)
    {
        VwEtlTableWithSource table = tableService.findOneTableInfo(tableId);
        if (table == null) {
            throw new ApiException(404, "Table not found");
        }
        return ResponseEntity.ok(table);
    }

    /**
     * 删除采集表
     *
     * @param tableId 采集表ID（路径参数）
     * @return 删除结果提示
     */
    @DeleteMapping("/{tableId}")
    public ResponseEntity<String> deleteTable(@PathVariable("tableId") long tableId)
    {
        if (!etlTableRepo.existsById(tableId)) {
            throw new ApiException(404, "Table not found");
        }
        tableService.deleteTable(tableId);
        return ResponseEntity.ok("表以及相关资源删除成功");
    }

    /**
     * 更新采集表
     *
     * @param tableId 采集表ID（路径参数）
     * @param etl 更新的采集表数据（请求体）
     * @return 更新后的采集表
     */
    @PutMapping("/{tableId}")
    public ResponseEntity<EtlTable> updateTable(@PathVariable("tableId") long tableId,
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
     *
     * @param tableId 采集表ID（路径参数）
     * @return 采集表字段列表
     */
    @GetMapping("/{tableId}/columns")
    public ResponseEntity<List<EtlColumn>> getTableColumns(@PathVariable("tableId") long tableId)
    {
        return ResponseEntity.ok(columnService.getColumns(tableId));
    }

    /**
     * 查询表采集统计
     *
     * @param tableId 采集表ID（路径参数）
     * @return 采集统计信息
     */
    @GetMapping("/{tableId}/statistics")
    public ResponseEntity<List<EtlStatistic>> getTableStatistics(@PathVariable("tableId") long tableId)
    {
        return ResponseEntity.ok(statService.getLast15Records(tableId));
    }

    /**
     * 批量保存采集表
     *
     * @param etls 采集表列表（请求体）
     * @return 保存的采集表数量
     */
    @PostMapping("/batch")
    public ResponseEntity<Integer> saveBatchTables(@RequestBody List<EtlTable> etls)
    {
        List<EtlTable> saveTables = tableService.batchCreateTable(etls);
        // 异步刷新资源并通知
        tableService.refreshTablesResourcesAsync(saveTables, getCurrentUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(etls.size());
    }

    /**
     * 新增单个采集表
     *
     * @param etl 采集表数据（请求体）
     * @return 新增的采集表
     */
    @PostMapping("")
    public ResponseEntity<EtlTable> saveTable(@RequestBody EtlTable etl)
    {
        EtlTable saved = tableService.createTable(etl);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 刷新所有表的关联资源
     * 触发一个异步任务，用于更新所有表的元数据（字段）和采集任务文件
     *
     * @param mode 刷新模式（查询参数），可选值: "all" 或 "need"，默认 "need"
     * @return 无内容响应（202 Accepted）或 400 当 mode 非法
     */
    @PostMapping("/actions/refresh")
    public ResponseEntity<Void> refreshAllTableResources(@RequestParam(value = "mode", defaultValue = "need") String mode)
    {
        if (!mode.equals("all") && !mode.equals("need")) {
            throw new ApiException(400, "Invalid mode parameter");
        }
        if (mode.equals("all")) {
            tableService.refreshAllTableResourcesAsyncWithNotify(getCurrentUsername());
        }
        else {
            tableService.refreshUpdatedTableResourcesAsyncWithNotify(getCurrentUsername());
        }
        return ResponseEntity.accepted().build();
    }

    /**
     * 刷新表关联资源
     * 触发一个异步任务，用于更新指定表的元数据（字段）和采集任务文件
     *
     * @param tableId 采集表ID（路径参数）
     * @return 任务结果
     */
    @PostMapping("/{tableId}/actions/refresh")
    public ResponseEntity<TaskResultDto> refreshTableResources(@PathVariable long tableId)
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
     *
     * @param params 表状态更新参数（请求体），示例: {"tids":[32,34],"status":"N","retryCnt":3}
     * @return 更新的表数量
     */
    @PostMapping("/batch/status")
    public ResponseEntity<Integer> batchUpdateStatus(@RequestBody BatchTableStatusDto params)
    {
        tableService.updateTableStatuses(params);
        return ResponseEntity.ok(1);
    }

    /**
     * 查询表视图
     *
     * @param params 查询参数（查询字符串形式）
     * @return 表视图列表
     */
    @GetMapping("/view")
    public ResponseEntity<List<VwEtlTableWithSource>> listTableViews(@RequestParam Map<String, String> params)
    {
        return ResponseEntity.ok(new ArrayList<>());
    }

    /**
     * 获取Addax Job模板
     *
     * @param tableId 采集表ID（路径参数）
     * @return Addax Job模板内容
     */
    @GetMapping("/{tableId}/addax-job")
    public ResponseEntity<String> getAddaxJob(@PathVariable long tableId)
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

    /**
     * 更新模板，这里允许用户临时更新模板
     * 比如临时修改某些参数进行测试或采集等
     * @param tableId 采集表ID（路径参数）
     * @param jobContent 作业内容（请求体）
     * @return 更新结果提示
     */
    @PutMapping("/{tableId}/addax-job")
    public ResponseEntity<String> updateAddaxJob(@PathVariable long tableId, @RequestBody String jobContent)
    {
        // 具体实现略
        jobContentService.updateJobContent(tableId, jobContent);
        return ResponseEntity.ok("Job content updated successfully");
    }

    private String getCurrentUsername()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getName();
    }
}
