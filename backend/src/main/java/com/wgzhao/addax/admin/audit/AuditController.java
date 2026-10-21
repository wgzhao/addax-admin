package com.wgzhao.addax.admin.audit;

import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit-events")
@RequiredArgsConstructor
public class AuditController
{
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditRepository auditRepository;
    private final AuditRecorder auditRecorder;

    @GetMapping("")
    public PageResponse<AuditEventDto> search(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String outcome,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) String resourceId,
        @RequestParam(required = false) String requestId,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int pageSize
    )
    {
        AuditRepository.AuditQuery query = buildQuery(
            from, to, actor, action, outcome, severity, resourceType, resourceId, requestId, q, page, pageSize);
        PageResponse<AuditEventDto> result = auditRepository.search(query);
        auditRecorder.success("AUDIT_EVENTS_VIEWED", "DATA_ACCESS", "INFO", "AUDIT_EVENT",
            null, null, null, null, Map.of("filters", filterSummary(query), "resultCount", result.getTotalElements()));
        return result;
    }

    @GetMapping("/{id}")
    public AuditEventDto detail(@PathVariable long id)
    {
        AuditEventDto result = auditRepository.findById(id);
        if (result == null) {
            throw new ApiException(404, "Audit event not found");
        }
        auditRecorder.success("AUDIT_EVENT_VIEWED", "DATA_ACCESS", "INFO", "AUDIT_EVENT",
            id, null, null, null);
        return result;
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String outcome,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) String resourceId,
        @RequestParam(required = false) String requestId,
        @RequestParam(required = false) String q
    )
    {
        AuditRepository.AuditQuery query = buildQuery(
            from, to, actor, action, outcome, severity, resourceType, resourceId, requestId, q, 0, MAX_PAGE_SIZE);
        List<AuditEventDto> rows = auditRepository.searchForExport(query, 10_000);
        String csv = toCsv(rows);
        auditRecorder.success("AUDIT_EVENTS_EXPORTED", "DATA_ACCESS", "WARNING", "AUDIT_EVENT",
            null, null, null, null, Map.of("filters", filterSummary(query), "resultCount", rows.size()));
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-events.csv\"")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(body);
    }

    private AuditRepository.AuditQuery buildQuery(
        OffsetDateTime from,
        OffsetDateTime to,
        String actor,
        String action,
        String outcome,
        String severity,
        String resourceType,
        String resourceId,
        String requestId,
        String q,
        int page,
        int pageSize
    )
    {
        OffsetDateTime effectiveTo = to == null ? OffsetDateTime.now() : to;
        OffsetDateTime effectiveFrom = from == null ? effectiveTo.minusDays(7) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ApiException(400, "from must not be after to");
        }
        if (effectiveFrom.isBefore(effectiveTo.minusDays(366))) {
            throw new ApiException(400, "audit query range must not exceed 366 days");
        }
        return new AuditRepository.AuditQuery(
            effectiveFrom,
            effectiveTo,
            actor,
            upper(action),
            upper(outcome),
            upper(severity),
            upper(resourceType),
            resourceId,
            requestId,
            q,
            Math.max(page, 0),
            Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE))
        );
    }

    private Map<String, Object> filterSummary(AuditRepository.AuditQuery query)
    {
        java.util.LinkedHashMap<String, Object> filters = new java.util.LinkedHashMap<>();
        filters.put("from", query.from());
        filters.put("to", query.to());
        filters.put("actor", query.actor());
        filters.put("action", query.action());
        filters.put("outcome", query.outcome());
        filters.put("severity", query.severity());
        filters.put("resourceType", query.resourceType());
        filters.put("resourceId", query.resourceId());
        filters.put("requestId", query.requestId());
        filters.put("q", query.q());
        return filters;
    }

    private String toCsv(List<AuditEventDto> rows)
    {
        StringBuilder csv = new StringBuilder(
            "id,occurred_at,request_id,actor_username,action,severity,resource_type,resource_id,outcome,client_ip,path\n");
        for (AuditEventDto row : rows) {
            csv.append(csv(row.id())).append(',')
                .append(csv(row.occurredAt())).append(',')
                .append(csv(row.requestId())).append(',')
                .append(csv(row.actorUsername())).append(',')
                .append(csv(row.action())).append(',')
                .append(csv(row.severity())).append(',')
                .append(csv(row.resourceType())).append(',')
                .append(csv(row.resourceId())).append(',')
                .append(csv(row.outcome())).append(',')
                .append(csv(row.clientIp())).append(',')
                .append(csv(row.requestPath())).append('\n');
        }
        return csv.toString();
    }

    private String csv(Object value)
    {
        if (value == null) {
            return "";
        }
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private String upper(String value)
    {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
