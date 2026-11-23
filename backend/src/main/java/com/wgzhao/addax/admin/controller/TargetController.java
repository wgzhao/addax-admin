package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.AddaxCheckItem;
import com.wgzhao.addax.admin.dto.EtlTargetView;
import com.wgzhao.addax.admin.dto.TargetHealthCheckPlan;
import com.wgzhao.addax.admin.dto.TargetHealthCheckResult;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.service.AuthzService;
import com.wgzhao.addax.admin.service.EtlTargetService;
import com.wgzhao.addax.admin.service.target.TargetHealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/targets")
@RequiredArgsConstructor
public class TargetController
{
    private final EtlTargetService etlTargetService;
    private final TargetHealthCheckService targetHealthCheckService;
    private final AuthzService authzService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public ResponseEntity<List<EtlTargetView>> listTargets(@RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly)
    {
        return ResponseEntity.ok(etlTargetService.listViews(enabledOnly, authzService.isAdmin()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EtlTargetView> getTarget(@PathVariable Long id)
    {
        return ResponseEntity.ok(etlTargetService.getView(id, authzService.isAdmin()));
    }

    @PostMapping("")
    @Transactional
    public ResponseEntity<EtlTarget> createTarget(@RequestBody EtlTarget target)
    {
        EtlTarget saved = etlTargetService.create(target);
        auditRecorder.success("TARGET_CREATED", "CONFIG", "WARNING", "ETL_TARGET",
            saved.getId(), saved.getName(), null, saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<EtlTarget> updateTarget(@PathVariable Long id, @RequestBody EtlTarget target)
    {
        EtlTarget before = etlTargetService.getById(id);
        java.util.Map<String, Object> beforeSnapshot = targetSnapshot(before);
        EtlTarget saved = etlTargetService.update(id, target);
        auditRecorder.success("TARGET_UPDATED", "CONFIG", "WARNING", "ETL_TARGET",
            id, saved.getName(), beforeSnapshot, targetSnapshot(saved));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteTarget(@PathVariable Long id)
    {
        EtlTarget before = etlTargetService.getById(id);
        etlTargetService.delete(id);
        auditRecorder.success("TARGET_DELETED", "CONFIG", "WARNING", "ETL_TARGET",
            id, before.getName(), before, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connect")
    @Transactional
    public ResponseEntity<Void> testTargetConnect(@PathVariable Long id)
    {
        etlTargetService.testConnect(id);
        auditRecorder.success("TARGET_CONNECTION_TESTED", "CONFIG", "INFO", "ETL_TARGET",
            id, null, null, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-connect")
    @Transactional
    public ResponseEntity<Void> testTargetConnect(@RequestBody EtlTarget target)
    {
        etlTargetService.testConnect(target);
        auditRecorder.success("TARGET_CONNECTION_TESTED", "CONFIG", "INFO", "ETL_TARGET",
            target.getId(), target.getName(), null, null);
        return ResponseEntity.noContent().build();
    }

    /**
     * 目标端健康检查：HDFS 目标端分项校验 Addax 运行时、写入模板、HiveServer2 以及一次真实的
     * HDFS 写入探针；RDBMS 目标端只校验 JDBC。支持未保存的表单（payload 直接携带 connectConfig）。
     */
    @PostMapping("/health-check")
    public ResponseEntity<TargetHealthCheckResult> healthCheck(@RequestBody EtlTarget target)
    {
        TargetHealthCheckResult result = targetHealthCheckService.check(target);
        auditRecorder.success("TARGET_HEALTH_CHECKED", "CONFIG", "INFO", "ETL_TARGET",
            target.getId(), target.getName(), null,
            Map.of("success", result.success(), "family", result.family()));
        return ResponseEntity.ok(result);
    }

    /**
     * 校验清单：前端先拿到全部检查项并全部展示，再逐个调用 {@code /health-check/items/{key}}
     * 回填结果，让耗时不同的检查项逐条打勾，而不是等全部结束后一次性弹出。
     */
    @PostMapping("/health-check/plan")
    public ResponseEntity<TargetHealthCheckPlan> healthCheckPlan(@RequestBody EtlTarget target)
    {
        TargetHealthCheckPlan plan = targetHealthCheckService.plan(target);
        auditRecorder.success("TARGET_HEALTH_CHECKED", "CONFIG", "INFO", "ETL_TARGET",
            target.getId(), target.getName(), null,
            Map.of("phase", "plan", "family", plan.family()));
        return ResponseEntity.ok(plan);
    }

    @PostMapping("/health-check/items/{key}")
    public ResponseEntity<AddaxCheckItem> healthCheckItem(@PathVariable String key,
        @RequestBody EtlTarget target)
    {
        return ResponseEntity.ok(targetHealthCheckService.runItem(target, key));
    }

    @PostMapping("/detect-dialect")
    public ResponseEntity<EtlTargetService.DialectInfo> detectDialect(@RequestBody Map<String, String> body)
    {
        return ResponseEntity.ok(etlTargetService.detectDialect(body.get("url"), body.get("targetType")));
    }

    private java.util.Map<String, Object> targetSnapshot(EtlTarget target)
    {
        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", target.getId());
        snapshot.put("code", target.getCode());
        snapshot.put("name", target.getName());
        snapshot.put("targetType", target.getTargetType());
        snapshot.put("connectConfig", target.getConnectConfig());
        snapshot.put("writerTemplateKey", target.getWriterTemplateKey());
        snapshot.put("enabled", target.getEnabled());
        snapshot.put("isDefault", target.getIsDefault());
        snapshot.put("remark", target.getRemark());
        return snapshot;
    }
}
