package com.wgzhao.addax.admin.alert.web;

import com.wgzhao.addax.admin.alert.AlertActors;
import com.wgzhao.addax.admin.alert.AlertDispatcher;
import com.wgzhao.addax.admin.alert.AlertQueryService;
import com.wgzhao.addax.admin.alert.dto.AlertEventDto;
import com.wgzhao.addax.admin.alert.dto.AlertTestEventRequest;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/alert-events")
@RequiredArgsConstructor
public class AlertEventController
{
    private final AlertQueryService queryService;
    private final AlertDispatcher dispatcher;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public PageResponse<AlertEventDto> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "eventType", required = false) String eventType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "severity", required = false) String severity,
        @RequestParam(value = "from", required = false) Instant from)
    {
        return queryService.events(page, pageSize, eventType, status, severity, from);
    }

    @PostMapping("/test")
    public AlertEventDto test(@RequestBody(required = false) AlertTestEventRequest request)
    {
        AlertEvent event = dispatcher.publishTest(request, AlertActors.currentUsername());
        auditRecorder.success("ALERT_TEST_EVENT_SENT", "ALERT", "INFO", "ALERT_EVENT",
            event.getId(), event.getEventKey(), null, null,
            Map.of("eventType", event.getEventType(), "severity", event.getSeverity()));
        return queryService.toDto(event);
    }
}
