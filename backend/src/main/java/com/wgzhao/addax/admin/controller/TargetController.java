package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.EtlTarget;
import com.wgzhao.addax.admin.service.EtlTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<EtlTarget> createTarget(@RequestBody EtlTarget target)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(etlTargetService.create(target));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EtlTarget> updateTarget(@PathVariable Long id, @RequestBody EtlTarget target)
    {
        return ResponseEntity.ok(etlTargetService.update(id, target));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTarget(@PathVariable Long id)
    {
        etlTargetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test-connect")
    public ResponseEntity<Void> testTargetConnect(@PathVariable Long id)
    {
        etlTargetService.testConnect(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test-connect")
    public ResponseEntity<Void> testTargetConnect(@RequestBody EtlTarget target)
    {
        etlTargetService.testConnect(target);
        return ResponseEntity.noContent().build();
    }
}
