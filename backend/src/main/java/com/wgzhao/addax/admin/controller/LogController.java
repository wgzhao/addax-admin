package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.dto.AddaxReportDto;
import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.AuthzService;
import com.wgzhao.addax.admin.service.CalendarService;
import com.wgzhao.addax.admin.service.EtlExecutionService;
import com.wgzhao.addax.admin.service.FailureService;
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
        authzService.requireTableRead(tableId);
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
    @PostMapping(value = "/job-report", consumes = "application/json")
    public boolean jobReport(@RequestBody AddaxReportDto dto)
    {
        log.debug("job report: {}", dto);
        long tid;
        Long executionId = null;
        try {
            String[] parts = dto.jobName().split(":", 2);
            tid = Long.parseLong(parts[0]);
            if (parts.length == 2) {
                executionId = Long.parseLong(parts[1]);
            }
        }
        catch (NumberFormatException e) {
            log.error("Invalid jobName format: {}", dto.jobName());
            return false;
        }
        if (tableService.getTable(tid) == null) {
            log.warn("Rejecting job report for unknown tid {}", tid);
            return false;
        }
        if (executionId != null && !executionService.belongsTo(executionId, tid)) {
            log.warn("Rejecting job report for execution {} that does not belong to tid {}", executionId, tid);
            return false;
        }
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
        // The execution snapshot knows the exact date the run was started for. A fillback run
        // carries a historical date, so deriving the date from "now" would make every fillback
        // overwrite the same etl_statistic row. Fall back to the table calendar only when the
        // report does not carry an execution id (legacy callers).
        LocalDate bizDate = executionId == null
            ? null
            : executionService.findBizDate(executionId).orElse(null);
        if (bizDate == null) {
            // The table's own calendar decides it: a source collecting on workdays or trading days
            // is stamped with a date the global business date does not match on the morning after a
            // holiday, and the missing-collection report reads this column back.
            bizDate = calendarService.businessDate(tableService.getSourceCalendar(tid));
        }
        sta.setBizDate(bizDate);
        boolean saved = statService.saveOrUpdate(sta);
        reconcileService.reconcile(tid, executionId, bizDate,
            dto.totalReadRecords(), dto.totalErrorRecords());
        return saved;
    }
}
