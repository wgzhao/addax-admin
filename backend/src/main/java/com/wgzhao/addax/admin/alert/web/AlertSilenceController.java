package com.wgzhao.addax.admin.alert.web;

import com.wgzhao.addax.admin.alert.AlertActors;
import com.wgzhao.addax.admin.alert.AlertSilenceService;
import com.wgzhao.addax.admin.alert.dto.AlertSilenceDto;
import com.wgzhao.addax.admin.alert.dto.AlertSilenceRequest;
import com.wgzhao.addax.admin.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/alert-silences")
@RequiredArgsConstructor
public class AlertSilenceController
{
    private final AlertSilenceService silenceService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public List<AlertSilenceDto> list()
    {
        return silenceService.list();
    }

    @PostMapping("")
    public AlertSilenceDto create(@RequestBody AlertSilenceRequest request)
    {
        AlertSilenceDto created = silenceService.create(request, AlertActors.currentUsername());
        auditRecorder.success("ALERT_SILENCE_CREATED", "ALERT", "WARNING", "ALERT_SILENCE",
            created.id(), created.scopeType(), null, created);
        return created;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id)
    {
        silenceService.delete(id);
        auditRecorder.success("ALERT_SILENCE_DELETED", "ALERT", "WARNING", "ALERT_SILENCE",
            id, null, null, null);
    }
}
