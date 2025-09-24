package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.service.ColumnService;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.utils.DsUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
public class TableController {

    @Autowired
    private TableService tableService;
    @Autowired
    private EtlTableRepo etlTableRepo;
    @Autowired
    private EtlSourceRepo etlSourceRepo;
    @Autowired
    private StatService statService;
    @Autowired
    private VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;
    @Resource
    DsUtil dsUtil;
    @Autowired private SystemConfigService systemConfigService;
    @Autowired private ColumnService columnService;
    @Autowired private JobContentService jobContentService;

    // 分页查询采集表
    @GetMapping("")
    public ResponseEntity<Page<VwEtlTableWithSource>> listTables(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        if (page < 0) page = 0;
        if (pageSize == -1) pageSize = Integer.MAX_VALUE;
        Page<VwEtlTableWithSource> result;
        if (status != null && !status.isEmpty()) {
            result = tableService.getTablesByStatus(page, pageSize, q, status, sortField, sortOrder);
        } else {
            result = tableService.getTablesInfo(page, pageSize, q, sortField, sortOrder);
        }
        return ResponseEntity.ok(result);
    }

    // 查询单个采集表
    @GetMapping("/{tableId}")
    public ResponseEntity<VwEtlTableWithSource> getTable(@PathVariable("tableId") long tableId) {
        VwEtlTableWithSource table = tableService.findOneTableInfo(tableId);
        if (table == null) throw new ApiException(404, "Table not found");
        return ResponseEntity.ok(table);
    }

    // 删除采集表
    @DeleteMapping("/{tableId}")
    public ResponseEntity<Void> deleteTable(@PathVariable("tableId") long tableId) {
        if (!etlTableRepo.existsById(tableId)) throw new ApiException(404, "Table not found");
        CompletableFuture.runAsync(() -> etlTableRepo.deleteById(tableId));
        return ResponseEntity.noContent().build();
    }

    // 查询表字段对比
    @GetMapping("/{tableId}/columns")
    public ResponseEntity<List<EtlColumn>> getTableColumns(@PathVariable("tableId") long tableId) {
        return ResponseEntity.ok(columnService.getColumns(tableId));
    }

    // 查询表采集统计
    @GetMapping("/{tableId}/statistics")
    public ResponseEntity<List<EtlStatistic>> getTableStatistics(@PathVariable("tableId") long tableId) {
        return ResponseEntity.ok(statService.getLast15Records(tableId));
    }

    // 查询所有源系统
    @GetMapping("/sources")
    public ResponseEntity<List<EtlSource>> listSources() {
        return ResponseEntity.ok(etlSourceRepo.findAllByEnabled(true));
    }


    // 批量保存采集表
    @PostMapping("/batch")
    public ResponseEntity<Integer> saveBatchTables(@RequestBody List<EtlTable> etls) {
        etlTableRepo.saveAll(etls);
        return ResponseEntity.status(HttpStatus.CREATED).body(etls.size());
    }

    // 新增单个采集表
    @PostMapping("/tables")
    public ResponseEntity<EtlTable> saveTable(@RequestBody EtlTable etl) {
        EtlTable saved = etlTableRepo.save(etl);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // 启动采集
    @PostMapping(path = "/{tableId}/start", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> startEtl(@PathVariable("tableId") long tableId, @RequestBody Map<String, String> payload) {
        Pair<Boolean, String> pair = dsUtil.execDs(payload.getOrDefault("ctype", "sp"), null);
        if (!pair.getFirst()) throw new ApiException(500, pair.getSecond());
        return ResponseEntity.ok(pair.getSecond());
    }

    // 更新表结构
    @PostMapping("/update-schema")
    public ResponseEntity<String> updateSchema(@RequestBody Map<String, Object> params) {
        // 具体实现略
        return ResponseEntity.ok("Schema updated");
    }

    // 批量更新表状态
    @PostMapping("/batch/status")
    public ResponseEntity<Integer> batchUpdateStatus(@RequestBody Map<String, Object> params) {
        // 具体实现略
        return ResponseEntity.ok(1);
    }

    // 查询表视图
    @GetMapping("/view")
    public ResponseEntity<List<VwEtlTableWithSource>> listTableViews(@RequestParam Map<String, String> params) {
        // 具体实现略
        return ResponseEntity.ok(new ArrayList<>());
    }

    // 获取Addax Job模板
    @GetMapping("/{tableId}/addax-job")
    public ResponseEntity<String> getAddaxJob(@PathVariable("tableId") long tableId) {
        // 具体实现略
        return ResponseEntity.ok("Addax Job Template");
    }
}
