package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.service.EtlTargetService;
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

@RestController
@RequestMapping("/targets")
@RequiredArgsConstructor
public class TargetController
{
    private final EtlTargetService etlTargetService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public ResponseEntity<List<EtlTarget>> listTargets(@RequestParam(value = "enabledOnly", required = false) Boolean enabledOnly)
    {
        return ResponseEntity.ok(etlTargetService.listAll(enabledOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EtlTarget> getTarget(@PathVariable Long id)
    {
        return ResponseEntity.ok(etlTargetService.getById(id));
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
