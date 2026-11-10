package com.wgzhao.addax.admin.alert.web;

import com.wgzhao.addax.admin.alert.AlertRuleService;
import com.wgzhao.addax.admin.alert.dto.AlertRuleDto;
import com.wgzhao.addax.admin.alert.dto.AlertRuleRequest;
import com.wgzhao.addax.admin.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController
{
    private final AlertRuleService ruleService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public List<AlertRuleDto> list()
    {
        return ruleService.list();
    }

    @PostMapping("")
    public AlertRuleDto create(@RequestBody AlertRuleRequest request)
    {
        AlertRuleDto created = ruleService.create(request);
        auditRecorder.success("ALERT_RULE_CREATED", "ALERT", "WARNING", "ALERT_RULE",
            created.id(), created.name(), null, created);
        return created;
    }

    @PutMapping("/{id}")
    public AlertRuleDto update(@PathVariable long id, @RequestBody AlertRuleRequest request)
    {
        AlertRuleDto before = ruleService.get(id);
        AlertRuleDto updated = ruleService.update(id, request);
        auditRecorder.success("ALERT_RULE_UPDATED", "ALERT", "WARNING", "ALERT_RULE",
            id, updated.name(), before, updated);
        return updated;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id)
    {
        AlertRuleDto before = ruleService.get(id);
        ruleService.delete(id);
        auditRecorder.success("ALERT_RULE_DELETED", "ALERT", "WARNING", "ALERT_RULE",
            id, before.name(), before, null);
    }
}
