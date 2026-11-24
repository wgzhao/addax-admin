package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.dto.AddaxReportDto;
import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.model.EtlExecution;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.AuthzService;
import com.wgzhao.addax.admin.service.CalendarService;
import com.wgzhao.addax.admin.service.CollectionSignalService;
import com.wgzhao.addax.admin.service.DataQualityService;
import com.wgzhao.addax.admin.service.EtlExecutionService;
import com.wgzhao.addax.admin.service.EtlJobQueueService;
import com.wgzhao.addax.admin.service.FailureService;
import com.wgzhao.addax.admin.service.JobReportTokenService;
import com.wgzhao.addax.admin.service.ReconcileService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TableService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志相关接口，主要用于获取采集日志和作业报告
 */
@RestController
@RequestMapping("/log")
@Slf4j
@AllArgsConstructor
public class LogController
{
    private final AddaxLogService addaxLogService;
    private final StatService statService;
    private final SystemConfigService configService;
    private final FailureService failureService;
    private final EtlExecutionService executionService;
    private final TableService tableService;
    private final CalendarService calendarService;
    private final AuthzService authzService;
    private final AuditRecorder auditRecorder;
    private final ReconcileService reconcileService;
    private final DataQualityService dataQualityService;
    private final CollectionSignalService collectionSignalService;
    private final EtlJobQueueService jobQueueService;
    private final JobReportTokenService jobReportTokenService;

    @GetMapping("/addax")
    public PageResponse<AddaxLog> listAddaxLog(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "sortField", required = false) String sortField,
        @RequestParam(value = "sortOrder", required = false) String sortOrder)
    {
        if (page < 0) {
            page = 0;
        }
        if (pageSize == -1) {
            pageSize = Integer.MAX_VALUE;
        }
        if (authzService.isGuest()) {
            throw new ApiException(403, "无权查看采集日志");
        }
        return PageResponse.from(addaxLogService.listWithPage(page, pageSize,
            authzService.operableSourceIds()));
    }

    /**
     * 获取指定采集任务的日志列表
     *
     * @param tid 采集任务 ID
     * @return 日志列表
     */
    @GetMapping("/addax/{tid}")
    public ApiResponse<List<AddaxLogDto>> getSpLog(@PathVariable String tid)
    {
        long tableId;
        try {
            tableId = Long.parseLong(tid);
        }
        catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid tid");
        }
        authzService.requireTableOperate(tableId);
        return ApiResponse.success(addaxLogService.getLogEntry(tid));
    }

    @PostMapping("/addax/cleanup")
    @Transactional
    public ApiResponse<String> cleanupAddaxLogs(@RequestParam("before") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate before)
    {
        addaxLogService.cleanupLogsBefore(before);
        auditRecorder.success("COLLECTION_LOGS_CLEANED", "DATA_ACCESS", "CRITICAL", "COLLECTION_LOG",
            before.toString(), "logs before " + before, null, null,
            java.util.Map.of("before", before.toString()));
        return ApiResponse.success("日志清理任务已提交");
    }

    @GetMapping("/jour/{tid}")
    public ApiResponse<String> getJourLog(@PathVariable Long tid)
    {
        authzService.requireTableOperate(tid);
        return ApiResponse.success(failureService.latestSummary(tid));
    }

    /**
     * 获取指定日志文件的内容
     *
     * @param id 日志 ID
     * @return 日志内容
     */
    @GetMapping("/addax/{id}/content")
    public ResponseEntity<String> getLogFileContent(@PathVariable Long id)
    {
        Long tid = addaxLogService.findTidById(id);
        if (tid == null) {
            throw new ApiException(404, "Log not found");
        }
        authzService.requireTableOperate(tid);
        String addaxLog = addaxLogService.getLogContent(id);
        return ResponseEntity.ok(addaxLog);
    }

    /**
     * 作业报告接口
     *
     * @param dto 作业报告数据
     * @return 是否成功
     */
    /** Reports are only meaningful while the attempt is recent; an old token must not revive it. */
    private static final Duration REPORT_VALIDITY = Duration.ofHours(24);

    @PostMapping(value = "/job-report", consumes = "application/json")
    @Transactional
    public boolean jobReport(@RequestBody AddaxReportDto dto)
    {
        ReportJobName jobName = parseReportJobName(dto.jobName());
        if (jobName == null) {
            // Never log the raw jobName: a valid one carries the report token.
            log.error("Invalid job report jobName (length={})",
                dto.jobName() == null ? 0 : dto.jobName().length());
            return false;
        }
        log.debug("job report: tid={}, executionId={}, readRecords={}, errorRecords={}",
            jobName.tid(), jobName.executionId(), dto.totalReadRecords(), dto.totalErrorRecords());
        if (jobName.tid() < 0) {
            return acceptSmokeReport(dto, jobName);
        }
        EtlExecution execution = executionService.findReportTarget(jobName.executionId(), jobName.tid())
            .orElse(null);
        if (execution == null) {
            log.warn("Rejecting job report for execution {} that does not belong to tid {}",
                jobName.executionId(), jobName.tid());
            return false;
        }
        if (!verifyReportAuthorship(execution, jobName.token())) {
            return false;
        }
        if (!isReportWindowOpen(execution)) {
            log.warn("Rejecting job report for execution {}: outside the {}h validity window",
                execution.getId(), REPORT_VALIDITY.toHours());
            return false;
        }
        if (tableService.getTable(jobName.tid()) == null) {
            log.warn("Rejecting job report for unknown tid {}", jobName.tid());
            return false;
        }
        // Durable one-shot gate. It lives in this transaction on purpose: a failure below rolls the
        // claim back so a retry can still persist the facts, while a commit blocks replays for good.
        if (!executionService.claimReport(execution.getId())) {
            log.warn("Rejecting duplicate job report for execution {}", execution.getId());
            return false;
        }
        EtlStatistic sta = buildStatistic(dto, jobName.tid(), execution.getId());
        // The execution snapshot knows the exact date the run was started for. A fillback run
        // carries a historical date, so deriving the date from "now" would make every fillback
        // overwrite the same etl_statistic row.
        LocalDate bizDate = execution.getBizDate();
        if (bizDate == null) {
            // Defensive: biz_date is NOT NULL in the schema. The table's own calendar decides it,
            // because a source collecting on workdays or trading days is stamped with a date the
            // global business date does not match on the morning after a holiday.
            bizDate = calendarService.businessDate(tableService.getSourceCalendar(jobName.tid()));
        }
        sta.setBizDate(bizDate);
        boolean saved = statService.saveOrUpdate(sta);
        reconcileService.reconcile(jobName.tid(), execution.getId(), bizDate,
            dto.totalReadRecords(), dto.totalErrorRecords());
        dataQualityService.checkRowCountDrift(jobName.tid(), execution.getId(), bizDate,
            dto.totalReadRecords());
        collectionSignalService.signalComplete(jobName.tid(), bizDate,
            dto.totalReadRecords(), dto.totalErrorRecords());
        return saved;
    }

    /**
     * Parsed addax {@code jobName}. Production attempts are {@code tid:executionId:token}; smoke
     * attempts are {@code -tid:token} because they have no execution row.
     */
    private record ReportJobName(long tid, Long executionId, String token)
    {
    }

    private static ReportJobName parseReportJobName(String jobName)
    {
        if (jobName == null || jobName.isBlank()) {
            return null;
        }
        String[] parts = jobName.split(":", 3);
        long tid;
        try {
            tid = Long.parseLong(parts[0].trim());
        }
        catch (NumberFormatException e) {
            return null;
        }
        if (tid < 0) {
            String token = parts.length >= 2 && !parts[1].isBlank() ? parts[1] : null;
            return new ReportJobName(tid, null, token);
        }
        if (parts.length < 2) {
            return null;
        }
        long executionId;
        try {
            executionId = Long.parseLong(parts[1].trim());
        }
        catch (NumberFormatException e) {
            return null;
        }
        String token = parts.length >= 3 && !parts[2].isBlank() ? parts[2] : null;
        return new ReportJobName(tid, executionId, token);
    }

    /**
     * Authorship only. One-shot semantics are enforced later by the durable claim in
     * {@link #jobReport}, so this method stays free of side effects and safe to call before the
     * transaction's business writes.
     */
    private boolean verifyReportAuthorship(EtlExecution execution, String token)
    {
        String expected = execution.getReportToken();
        if (expected != null) {
            if (!JobReportTokenService.matches(expected, token)) {
                log.warn("Rejecting job report for execution {}: report token mismatch", execution.getId());
                return false;
            }
            return true;
        }
        if (!jobQueueService.isRunning(execution.getJobQueueId())) {
            log.warn("Rejecting tokenless job report for execution {}: queue row is not running",
                execution.getId());
            return false;
        }
        log.warn("Accepting legacy tokenless job report for execution {} (queue row {} is running)",
            execution.getId(), execution.getJobQueueId());
        return true;
    }

    private static boolean isReportWindowOpen(EtlExecution execution)
    {
        LocalDateTime startedAt = execution.getStartedAt();
        return startedAt == null || startedAt.plus(REPORT_VALIDITY).isAfter(LocalDateTime.now());
    }

    /**
     * Negative tids are reserved for smoke checks (addax-directory validation, target health check).
     * They have no table or execution behind them, so the token registered by the caller in Redis is
     * the only proof that this is a legitimate probe; downstream side effects are skipped.
     */
    private boolean acceptSmokeReport(AddaxReportDto dto, ReportJobName jobName)
    {
        if (jobName.tid() != Constants.SMOKE_TEST_TID
            && jobName.tid() != Constants.TARGET_SMOKE_TEST_TID) {
            log.warn("Rejecting job report for unregistered negative tid {}", jobName.tid());
            return false;
        }
        boolean verified;
        try {
            verified = jobReportTokenService.consumeSmokeToken(jobName.tid(), jobName.token());
        }
        catch (Exception e) {
            log.warn("Cannot verify smoke report token for tid {}: {}", jobName.tid(), e.getMessage());
            return false;
        }
        if (!verified) {
            log.warn("Rejecting unverifiable smoke job report for tid {}", jobName.tid());
            return false;
        }
        EtlStatistic sta = buildStatistic(dto, jobName.tid(), null);
        sta.setBizDate(LocalDate.now());
        return statService.saveOrUpdate(sta);
    }

    private static EtlStatistic buildStatistic(AddaxReportDto dto, long tid, Long executionId)
    {
        EtlStatistic sta = new EtlStatistic();
        sta.setTid(tid);
        sta.setExecutionId(executionId);
        sta.setStartAt(LocalDateTime.ofEpochSecond(dto.startTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setEndAt(LocalDateTime.ofEpochSecond(dto.endTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setTakeSecs(dto.totalCosts());
        sta.setByteSpeed(dto.byteSpeedPerSecond());
        sta.setRecSpeed(dto.recordSpeedPerSecond());
        sta.setTotalRecs(dto.totalReadRecords());
        sta.setTotalErrors(dto.totalErrorRecords());
        sta.setTotalBytes(dto.byteSpeedPerSecond() * dto.totalCosts());
        return sta;
    }
}
