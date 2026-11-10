package com.wgzhao.addax.admin.alert.web;

import com.wgzhao.addax.admin.alert.AlertActors;
import com.wgzhao.addax.admin.alert.AlertChannelService;
import com.wgzhao.addax.admin.alert.dto.AlertChannelDto;
import com.wgzhao.addax.admin.alert.dto.AlertChannelRequest;
import com.wgzhao.addax.admin.alert.dto.AlertChannelTestResult;
import com.wgzhao.addax.admin.alert.dto.AlertSummaryDto;
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
import java.util.Map;

@RestController
@RequestMapping("/alert-channels")
@RequiredArgsConstructor
public class AlertChannelController
{
    private final AlertChannelService channelService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public List<AlertChannelDto> list()
    {
        return channelService.list();
    }

    @GetMapping("/summary")
    public AlertSummaryDto summary()
    {
        return channelService.summary();
    }

    @PostMapping("")
    public AlertChannelDto create(@RequestBody AlertChannelRequest request)
    {
        String username = AlertActors.currentUsername();
        AlertChannelDto created = channelService.create(request, username);
        auditRecorder.success("ALERT_CHANNEL_CREATED", "ALERT", "WARNING", "ALERT_CHANNEL",
            created.id(), created.name(), null, created);
        return created;
    }

    @PutMapping("/{id}")
    public AlertChannelDto update(@PathVariable long id, @RequestBody AlertChannelRequest request)
    {
        AlertChannelDto before = channelService.get(id);
        AlertChannelDto updated = channelService.update(id, request);
        auditRecorder.success("ALERT_CHANNEL_UPDATED", "ALERT", "WARNING", "ALERT_CHANNEL",
            id, updated.name(), before, updated);
        return updated;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable long id)
    {
        AlertChannelDto before = channelService.get(id);
        channelService.delete(id);
        auditRecorder.success("ALERT_CHANNEL_DELETED", "ALERT", "WARNING", "ALERT_CHANNEL",
            id, before.name(), before, null);
    }

    @PostMapping("/{id}/test")
    public AlertChannelTestResult test(@PathVariable long id)
    {
        AlertChannelDto channel = channelService.get(id);
        AlertChannelTestResult result = channelService.test(id);
        auditRecorder.success("ALERT_CHANNEL_TESTED", "ALERT", "INFO", "ALERT_CHANNEL",
            id, channel.name(), null, null,
            Map.of("ok", result.ok(), "responseCode", result.responseCode() == null ? 0 : result.responseCode(),
                "latencyMs", result.latencyMs()));
        return result;
    }
}
