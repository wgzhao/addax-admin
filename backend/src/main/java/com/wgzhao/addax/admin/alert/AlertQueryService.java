package com.wgzhao.addax.admin.alert;

import tools.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.alert.dto.AlertDeliveryDto;
import com.wgzhao.addax.admin.alert.dto.AlertEventDto;
import com.wgzhao.addax.admin.alert.model.AlertDelivery;
import com.wgzhao.addax.admin.alert.model.AlertEvent;
import com.wgzhao.addax.admin.alert.repo.AlertDeliveryRepo;
import com.wgzhao.addax.admin.alert.repo.AlertEventRepo;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertQueryService
{
    private final AlertEventRepo eventRepo;
    private final AlertDeliveryRepo deliveryRepo;

    public PageResponse<AlertEventDto> events(int page, int pageSize, String eventType,
        String status, String severity, Instant from)
    {
        AlertEventStatus eventStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                eventStatus = AlertEventStatus.valueOf(status.trim().toUpperCase());
            }
            catch (IllegalArgumentException ex) {
                throw new ApiException(400, "无效的事件状态: " + status);
            }
        }
        Specification<AlertEvent> spec = (root, query, cb) -> cb.conjunction();
        String typeFilter = blankToNull(eventType);
        if (typeFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventType"), typeFilter));
        }
        AlertEventStatus statusFilter = eventStatus;
        if (statusFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), statusFilter));
        }
        String severityFilter = blankToNull(severity);
        if (severityFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("severity"), severityFilter));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastSeen"), from));
        }
        Page<AlertEvent> result = eventRepo.findAll(spec,
            pageRequest(page, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(result.map(this::toDto));
    }

    public PageResponse<AlertDeliveryDto> deliveries(int page, int pageSize, String status,
        Long channelId, Long eventId)
    {
        AlertDeliveryStatus deliveryStatus = AlertDeliveryStatus.parse(status);
        if (status != null && !status.isBlank() && deliveryStatus == null) {
            throw new ApiException(400, "无效的投递状态: " + status);
        }
        Specification<AlertDelivery> spec = (root, query, cb) -> cb.conjunction();
        if (deliveryStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), deliveryStatus));
        }
        if (channelId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("channelId"), channelId));
        }
        if (eventId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventId"), eventId));
        }
        Page<AlertDelivery> result = deliveryRepo.findAll(spec,
            pageRequest(page, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        List<Long> eventIds = result.getContent().stream().map(AlertDelivery::getEventId).distinct().toList();
        Map<Long, AlertEvent> events = eventRepo.findAllById(eventIds).stream()
            .collect(Collectors.toMap(AlertEvent::getId, Function.identity()));
        return PageResponse.from(result.map(delivery -> toDto(delivery, events.get(delivery.getEventId()))));
    }

    @Transactional
    public AlertDeliveryDto retry(long id)
    {
        AlertDelivery delivery = deliveryRepo.findById(id)
            .orElseThrow(() -> new ApiException(404, "投递记录不存在: " + id));
        if (delivery.getStatus() != AlertDeliveryStatus.FAILED
            && delivery.getStatus() != AlertDeliveryStatus.DEAD) {
            throw new ApiException(400, "只有失败或死信的投递可以手动重试");
        }
        delivery.setStatus(AlertDeliveryStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextRetryAt(Instant.now());
        delivery.setLastError(null);
        delivery.setResponseCode(null);
        delivery.setResponseBody(null);
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        deliveryRepo.save(delivery);
        AlertEvent event = eventRepo.findById(delivery.getEventId()).orElse(null);
        return toDto(delivery, event);
    }

    public AlertEventDto toDto(AlertEvent event)
    {
        JsonNode payload = event.getPayload();
        String title = payload == null ? event.getEventKey() : payload.path("title").asText(event.getEventKey());
        String text = payload == null ? "" : payload.path("text").asText("");
        return new AlertEventDto(event.getId(), event.getEventKey(), event.getEventType(),
            event.getSeverity(), event.getScopeType(), event.getScopeValue(),
            event.getStatus().name(), event.getFirstSeen(), event.getLastSeen(),
            event.getFireCount(), event.getResolvedAt(), title, text);
    }

    private AlertDeliveryDto toDto(AlertDelivery delivery, AlertEvent event)
    {
        JsonNode payload = event == null ? null : event.getPayload();
        String title = event == null ? null : (payload == null ? event.getEventKey()
            : payload.path("title").asText(event.getEventKey()));
        return new AlertDeliveryDto(delivery.getId(), delivery.getEventId(), title,
            event == null ? null : event.getEventType(),
            event == null ? null : event.getSeverity(),
            delivery.getChannelId(), delivery.getChannelName(), delivery.getStatus().name(),
            delivery.getAttempts(), delivery.getNextRetryAt(), delivery.getResponseCode(),
            delivery.getResponseBody(), delivery.getLastError(), delivery.getSentAt(),
            delivery.getCreatedAt(), delivery.getUpdatedAt());
    }

    private PageRequest pageRequest(int page, int pageSize, Sort sort)
    {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(pageSize, 1), 200);
        return PageRequest.of(safePage, safeSize, sort);
    }

    private String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
