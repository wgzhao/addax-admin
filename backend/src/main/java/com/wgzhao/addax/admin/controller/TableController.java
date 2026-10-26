package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.audit.AuditSanitizer;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.dto.BatchTableStatusDto;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreview;
import com.wgzhao.addax.admin.dto.CollectionSchemaPreviewRequest;
import com.wgzhao.addax.admin.dto.CollectionPlanPreview;
import com.wgzhao.addax.admin.dto.CollectionPlanCommitItem;
import com.wgzhao.addax.admin.dto.CollectionPlanCommitResult;
import com.wgzhao.addax.admin.dto.ColumnMappingDto;
import com.wgzhao.addax.admin.dto.ColumnMappingInput;
import com.wgzhao.addax.admin.dto.ExternalCollectionCreateRequest;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlExecution;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.EtlTableChangeLog;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.service.ColumnService;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.CollectionSchemaPreviewService;
import com.wgzhao.addax.admin.service.CollectionPlanPreviewService;
import com.wgzhao.addax.admin.service.CollectionPlanCommitService;
import com.wgzhao.addax.admin.service.EtlJobQueueService;
import com.wgzhao.addax.admin.service.EtlTableChangeLogService;
import com.wgzhao.addax.admin.service.EtlExecutionService;
import com.wgzhao.addax.admin.service.ExternalCollectionService;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TargetContractService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.stream.Collectors;

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
    private final ColumnMappingService columnMappingService;
    private final CollectionSchemaPreviewService schemaPreviewService;
    private final CollectionPlanPreviewService collectionPlanPreviewService;
    private final CollectionPlanCommitService collectionPlanCommitService;
    private final ExternalCollectionService externalCollectionService;
    private final TargetContractService targetContractService;
    private final EtlJobQueueService jobQueueService;
    /**
     * 作业内容服务
     */
    private final JobContentService jobContentService;
    private final EtlTableChangeLogService changeLogService;
    private final EtlExecutionService executionService;
    private final AuditRecorder auditRecorder;
    private final AuditSanitizer auditSanitizer;

    /**
     * 分页查询采集表
     *
     * @param page 页码（查询参数），默认 0
     * @param pageSize 每页记录数（查询参数），默认 10，-1 表示不分页
     * @param q 查询关键字（查询参数），可选
    * @param statuses 状态列表（查询参数，逗号分隔），可选
     * @param sortField 排序字段（查询参数），可选
     * @param sortOrder 排序顺序（查询参数），可选
     * @return 采集表分页结果
     */
    @GetMapping("")
    public ResponseEntity<PageResponse<VwEtlTableWithSource>> listTables(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "statuses", required = false) String statuses,
        @RequestParam(value = "sourceId", required = false) Integer sourceId,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "lifecycle", required = false) String lifecycle,
        @RequestParam(value = "sortField", required = false) String sortField,
        @RequestParam(value = "sortOrder", required = false) String sortOrder)
    {
        if (page < 0) {
            page = 0;
        }
        if (pageSize == -1) {
            pageSize = Integer.MAX_VALUE;
        }

        String requestedScope = scope != null ? scope : lifecycle;
        String normalizedScope = requestedScope == null ? "CURRENT" : requestedScope.toUpperCase();
        if (normalizedScope.equals("ACTIVE")) {
            // Keep old bookmarks working; ACTIVE previously meant non-retired tables.
            normalizedScope = "CURRENT";
        }
        if (!normalizedScope.equals("CURRENT")
            && !normalizedScope.equals("DORMANT")
            && !normalizedScope.equals("RETIRED")
            && !normalizedScope.equals("ALL")) {
            throw new ApiException(400, "scope must be CURRENT, DORMANT, RETIRED, or ALL");
        }
        List<String> statusList = parseStatuses(statuses);
        Page<VwEtlTableWithSource> result;
        result = tableService.findTables(
            page, pageSize, q, statusList, sourceId, sortField, sortOrder, normalizedScope);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    private List<String> parseStatuses(String statuses)
    {
        if (statuses == null || statuses.isBlank()) {
            return List.of();
        }
        return List.of(statuses.split(","))
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toUpperCase)
            .distinct()
            .collect(Collectors.toList());
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
    @Transactional
    public ResponseEntity<String> deleteTable(@PathVariable("tableId") long tableId)
    {
        EtlTable before = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        tableService.retireTable(tableId, getCurrentUsername());
        EtlTable after = etlTableRepo.findById(tableId).orElse(null);
        auditRecorder.success("TABLE_RETIRED", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            tableId, before.getSourceTable(), before, after);
        return ResponseEntity.ok("采集表已退役，历史统计和日志已保留");
    }

    @PostMapping("/{tableId}/restore")
    @Transactional
    public ResponseEntity<VwEtlTableWithSource> restoreTable(@PathVariable("tableId") long tableId)
    {
        EtlTable before = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        tableService.restoreTable(tableId);
        VwEtlTableWithSource after = tableService.findOneTableInfo(tableId);
        auditRecorder.success("TABLE_RESTORED", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            tableId, before.getSourceTable(), before, after);
        return ResponseEntity.ok(after);
    }

    @GetMapping("/{tableId}/executions")
    public ResponseEntity<PageResponse<EtlExecution>> getExecutions(
        @PathVariable("tableId") long tableId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize)
    {
        return ResponseEntity.ok(PageResponse.from(executionService.list(tableId, page, pageSize)));
    }

    /**
     * 更新采集表
     *
     * @param tableId 采集表ID（路径参数）
     * @param etl 更新的采集表数据（请求体）
     * @return 更新后的采集表
     */
    @PutMapping("/{tableId}")
    @Transactional
    public ResponseEntity<EtlTable> updateTable(@PathVariable("tableId") long tableId,
        @RequestBody EtlTable etl)
    {
        if (etl.getId() == null || etl.getId() != tableId) {
            throw new ApiException(400, "Table ID in path and body must match");
        }
        EtlTable existing = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        if (existing.getRetiredAt() != null) {
            throw new ApiException(409, "采集表已退役，恢复后才能修改");
        }
        EtlTable updated = tableService.updateTable(etl, getCurrentUsername());
        auditRecorder.success("TABLE_UPDATED", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            tableId, updated.getSourceTable(), existing, updated);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{tableId}/changes")
    public ResponseEntity<PageResponse<EtlTableChangeLog>> getTableChanges(
        @PathVariable("tableId") long tableId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(value = "field", required = false) String field)
    {
        if (!etlTableRepo.existsById(tableId)) {
            throw new ApiException(404, "Table not found");
        }
        if (page < 0) {
            page = 0;
        }
        if (pageSize == -1) {
            pageSize = Integer.MAX_VALUE;
        }
        Page<EtlTableChangeLog> result = changeLogService.getTableChanges(tableId, page, pageSize, field);
        return ResponseEntity.ok(PageResponse.from(result));
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

    @GetMapping("/{tableId}/column-mappings")
    public ResponseEntity<List<ColumnMappingDto>> getColumnMappings(@PathVariable("tableId") long tableId)
    {
        if (!etlTableRepo.existsById(tableId))
        {
            throw new ApiException(404, "Table not found");
        }
        columnMappingService.reconcile(tableId);
        return ResponseEntity.ok(columnMappingService.list(tableId));
    }

    @PutMapping("/{tableId}/column-mappings")
    public ResponseEntity<List<ColumnMappingDto>> replaceColumnMappings(
        @PathVariable("tableId") long tableId,
        @RequestBody List<ColumnMappingInput> mappings)
    {
        EtlTable table = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        if (table.getRetiredAt() != null)
        {
            throw new ApiException(409, "采集表已退役，恢复后才能修改字段映射");
        }
        if (!jobQueueService.findActiveByTid(tableId).isEmpty())
        {
            throw new ApiException(409, "Table has an active queued or running job");
        }
        List<ColumnMappingDto> saved = columnMappingService.replace(tableId, mappings);
        TaskResultDto refresh = tableService.refreshTableResources(tableId);
        if (!refresh.success())
        {
            throw new ApiException(409, refresh.message());
        }
        auditRecorder.success("TABLE_COLUMN_MAPPINGS_UPDATED", "COLLECTION_DEFINITION", "WARNING",
            "ETL_TABLE", tableId, table.getSourceTable(), null, saved);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{tableId}/target-contract/check")
    public ResponseEntity<TargetContractReport> checkTargetContract(@PathVariable long tableId)
    {
        VwEtlTableWithSource table = tableService.getTableView(tableId);
        if (table == null)
        {
            throw new ApiException(404, "Table not found");
        }
        return ResponseEntity.ok(targetContractService.check(table));
    }

    @PostMapping("/schema-preview")
    public ResponseEntity<CollectionSchemaPreview> previewExternalSchema(
        @RequestBody CollectionSchemaPreviewRequest request)
    {
        return ResponseEntity.ok(schemaPreviewService.preview(request));
    }

    @PostMapping("/schema-plans/preview")
    public ResponseEntity<List<CollectionPlanPreview>> previewCollectionPlans(
        @RequestBody List<CollectionSchemaPreviewRequest> requests)
    {
        return ResponseEntity.ok(collectionPlanPreviewService.preview(requests));
    }

    @PostMapping("/schema-plans/commit")
    public ResponseEntity<List<CollectionPlanCommitResult>> commitCollectionPlans(
        @RequestBody List<CollectionPlanCommitItem> items)
    {
        List<CollectionPlanCommitResult> results = collectionPlanCommitService.commit(items);
        for (CollectionPlanCommitResult result : results)
        {
            String resourceName = result.sourceTable() + " -> " + result.targetTable();
            if ("CREATED".equals(result.status()))
            {
                auditRecorder.success("COLLECTION_PLAN_CREATED", "COLLECTION_DEFINITION",
                    "WARNING", "ETL_TABLE", result.tableId(), resourceName, null, result);
            }
            else
            {
                auditRecorder.failed("COLLECTION_PLAN_CREATE_FAILED", "COLLECTION_DEFINITION",
                    "WARNING", "ETL_TABLE", result.tableId(), resourceName, "CREATE_FAILED",
                    result.message(), Map.of("sourceTable", result.sourceTable(),
                        "targetTable", result.targetTable()));
            }
        }
        return ResponseEntity.ok(results);
    }

    @PostMapping("/external")
    public ResponseEntity<EtlTable> createExternalTable(@RequestBody ExternalCollectionCreateRequest request)
    {
        EtlTable saved = externalCollectionService.create(request);
        auditRecorder.success("EXTERNAL_TABLE_CREATED", "COLLECTION_DEFINITION", "WARNING",
            "ETL_TABLE", saved.getId(), saved.getSourceTable(), null, saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
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
        if (etls.stream().anyMatch(table ->
            table.getSchemaManagementMode() == SchemaManagementMode.EXTERNAL))
        {
            throw new ApiException(400, "External tables require schema preview and confirmed mappings");
        }
        List<EtlTable> saveTables = tableService.batchCreateTable(etls);
        // 异步刷新资源并通知
        tableService.refreshTablesResourcesAsync(saveTables, getCurrentUsername());
        auditRecorder.success("TABLES_BATCH_CREATED", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            "batch", "batch create", null, null,
            java.util.Map.of("count", saveTables.size(),
                "tableIds", saveTables.stream().map(EtlTable::getId).toList()));
        return ResponseEntity.status(HttpStatus.CREATED).body(etls.size());
    }

    /**
     * 新增单个采集表
     *
     * @param etl 采集表数据（请求体）
     * @return 新增的采集表
     */
    @PostMapping("")
    @Transactional
    public ResponseEntity<EtlTable> saveTable(@RequestBody EtlTable etl)
    {
        if (etl.getSchemaManagementMode() == SchemaManagementMode.EXTERNAL)
        {
            throw new ApiException(400, "External tables require schema preview and confirmed mappings");
        }
        EtlTable saved = tableService.createTable(etl);
        auditRecorder.success("TABLE_CREATED", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            saved.getId(), saved.getSourceTable(), null, saved);
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
    @Transactional
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
        auditRecorder.accepted("TABLE_RESOURCES_REFRESH_REQUESTED", "COLLECTION_DEFINITION",
            "WARNING", "ETL_TABLE", "all", "all tables", java.util.Map.of("mode", mode));
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
    @Transactional
    public ResponseEntity<TaskResultDto> refreshTableResources(@PathVariable long tableId)
    {
        EtlTable table = etlTableRepo.findById(tableId).orElse(null);
        if (table == null) {
            return ResponseEntity.status(400).body(TaskResultDto.failure("tableId 对应的采集表不存在", 0));
        }
        if (table.getRetiredAt() != null) {
            return ResponseEntity.status(409).body(TaskResultDto.failure("采集表已退役，恢复后才能刷新", 0));
        }
        TaskResultDto taskResultDto = tableService.refreshTableResources(tableId);
        if (taskResultDto.success()) {
            auditRecorder.success("TABLE_RESOURCES_REFRESHED", "COLLECTION_DEFINITION", "WARNING",
                "ETL_TABLE", tableId, table.getSourceTable(), null, null);
            return ResponseEntity.ok(taskResultDto);
        }
        else {
            auditRecorder.failed("TABLE_RESOURCES_REFRESHED", "COLLECTION_DEFINITION", "WARNING",
                "ETL_TABLE", tableId, table.getSourceTable(), "REFRESH_FAILED",
                taskResultDto.message(), java.util.Map.of());
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
    @Transactional
    public ResponseEntity<Integer> batchUpdateStatus(@RequestBody BatchTableStatusDto params)
    {
        tableService.updateTableStatuses(params);
        auditRecorder.success("TABLE_STATUSES_UPDATED", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            "batch", "batch status", null, null,
            java.util.Map.of("tids", params.tids(), "status", params.status(),
                "retryCnt", params.retryCnt()));
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
    @Transactional
    public ResponseEntity<String> updateAddaxJob(@PathVariable long tableId, @RequestBody String jobContent)
    {
        String before = jobContentService.getJobContent(tableId);
        jobContentService.updateJobContent(tableId, jobContent);
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("beforeDigest", auditSanitizer.digest(before));
        metadata.put("afterDigest", auditSanitizer.digest(jobContent));
        metadata.put("beforeLength", before == null ? 0 : before.length());
        metadata.put("afterLength", jobContent.length());
        auditRecorder.success("TABLE_JOB_OVERRIDDEN", "COLLECTION_DEFINITION", "WARNING", "ETL_TABLE",
            tableId, null, null, null,
            metadata);
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
