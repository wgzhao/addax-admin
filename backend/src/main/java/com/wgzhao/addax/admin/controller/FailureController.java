package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.FailureCleanupRequest;
import com.wgzhao.addax.admin.dto.FailureCleanupResult;
import com.wgzhao.addax.admin.dto.FailureDetailDto;
import com.wgzhao.addax.admin.dto.FailureDto;
import com.wgzhao.addax.admin.dto.FailureGroupDto;
import com.wgzhao.addax.admin.dto.FailureStatsDto;
import com.wgzhao.addax.admin.dto.LatestFailureDto;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.service.AuthzService;
import com.wgzhao.addax.admin.service.FailureService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Failure facts: the collection failure history, the status-drawer diagnosis and history cleanup.
 */
@RestController
@RequestMapping("/failures")
@Slf4j
@AllArgsConstructor
public class FailureController
{
    private final FailureService failureService;
    private final AuthzService authzService;
    private final AuditRecorder auditRecorder;

    @GetMapping
    public PageResponse<FailureDto> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
        @RequestParam(value = "tid", required = false) Long tid,
        @RequestParam(value = "sourceId", required = false) Integer sourceId,
        @RequestParam(value = "stage", required = false) String stage,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "fingerprint", required = false) String fingerprint,
        @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "finalOnly", defaultValue = "false") boolean finalOnly)
    {
        if (tid != null) {
            authzService.requireTableRead(tid);
        }
        if (sourceId != null) {
            authzService.requireSourceRead(sourceId);
        }
        if (pageSize == -1) {
            pageSize = Integer.MAX_VALUE;
        }
        return failureService.list(page, pageSize, tid, sourceId, stage, category, fingerprint,
            from, to, q, finalOnly);
    }

    @GetMapping("/latest")
    public Map<Long, LatestFailureDto> latest(@RequestParam("tids") String tids)
    {
        List<Long> ids = parseTids(tids);
        return failureService.latestForTables(ids);
    }

    @GetMapping("/tables/{tid}/latest")
    public LatestFailureDto latestForTable(@PathVariable long tid)
    {
        authzService.requireTableRead(tid);
        return failureService.latest(tid);
    }

    @GetMapping("/{id}")
    public FailureDetailDto detail(@PathVariable long id)
    {
        FailureDetailDto detail = failureService.detail(id);
        if (detail == null) {
            throw new ApiException(404, "失败记录不存在");
        }
        authzService.requireTableRead(detail.failure().tid());
        return detail;
    }

    @GetMapping("/stats")
    public FailureStatsDto stats(
        @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
        @RequestParam(value = "tid", required = false) Long tid,
        @RequestParam(value = "sourceId", required = false) Integer sourceId,
        @RequestParam(value = "stage", required = false) String stage,
        @RequestParam(value = "category", required = false) String category)
    {
        return failureService.stats(from, to, tid, sourceId, stage, category);
    }

    @GetMapping("/groups")
    public List<FailureGroupDto> groups(
        @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
        @RequestParam(value = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
        @RequestParam(value = "tid", required = false) Long tid,
        @RequestParam(value = "sourceId", required = false) Integer sourceId,
        @RequestParam(value = "stage", required = false) String stage,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "finalOnly", defaultValue = "false") boolean finalOnly,
        @RequestParam(value = "limit", defaultValue = "200") int limit)
    {
        return failureService.groups(from, to, tid, sourceId, stage, category, q, finalOnly, limit);
    }

    /**
     * Delete failure facts older than the cutoff. Raw logs and executions are untouched.
     */
    @PostMapping("/cleanup")
    public FailureCleanupResult cleanup(@RequestBody FailureCleanupRequest request)
    {
        authzService.requireAdmin();
        if (request == null || request.before() == null) {
            throw new ApiException(400, "必须指定清理截止日期");
        }
        FailureCleanupResult result = failureService.cleanup(request);
        if (!request.dryRun()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("before", request.before().toString());
            metadata.put("tid", request.tid());
            metadata.put("sourceId", request.sourceId());
            metadata.put("stage", request.stage());
            metadata.put("matched", result.matched());
            metadata.put("deleted", result.deleted());
            auditRecorder.success("COLLECTION_FAILURES_CLEANED", "DATA_ACCESS", "CRITICAL",
                "COLLECTION_FAILURE", "failures", "failure facts", null, null, metadata);
        }
        return result;
    }

    private List<Long> parseTids(String raw)
    {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> {
                try {
                    return Long.parseLong(value);
                }
                catch (NumberFormatException e) {
                    throw new ApiException(400, "Invalid tids");
                }
            })
            .distinct()
            .limit(200)
            .toList();
    }
}
