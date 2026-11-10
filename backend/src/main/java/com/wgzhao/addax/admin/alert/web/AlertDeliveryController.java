package com.wgzhao.addax.admin.alert.web;

import com.wgzhao.addax.admin.alert.AlertQueryService;
import com.wgzhao.addax.admin.alert.dto.AlertDeliveryDto;
import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/alert-deliveries")
@RequiredArgsConstructor
public class AlertDeliveryController
{
    private final AlertQueryService queryService;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public PageResponse<AlertDeliveryDto> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "channelId", required = false) Long channelId,
        @RequestParam(value = "eventId", required = false) Long eventId)
    {
        return queryService.deliveries(page, pageSize, status, channelId, eventId);
    }

    @PostMapping("/{id}/retry")
    public AlertDeliveryDto retry(@PathVariable long id)
    {
        AlertDeliveryDto retried = queryService.retry(id);
        auditRecorder.success("ALERT_DELIVERY_RETRIED", "ALERT", "WARNING", "ALERT_DELIVERY",
            id, retried.channelName(), null, retried);
        return retried;
    }
}
