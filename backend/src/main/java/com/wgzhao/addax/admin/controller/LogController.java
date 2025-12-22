package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.dto.AddaxReportDto;
import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.PageResponse;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
    private final EtlJourService jourService;

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
        return PageResponse.from(addaxLogService.listWithPage(page, pageSize));
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
        return ApiResponse.success(addaxLogService.getLogEntry(tid));
    }

    @PostMapping("/addax/cleanup")
    public ApiResponse<String> cleanupAddaxLogs(@RequestParam("before") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate before)
    {
        addaxLogService.cleanupLogsBefore(before);
        return ApiResponse.success("日志清理任务已提交");
    }

    @GetMapping("/jour/{tid}")
    public ApiResponse<String> getJourLog(@PathVariable Long tid)
    {
        return ApiResponse.success(jourService.findLastErrorByTableId(tid));
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
        try {
            Long.parseLong(dto.jobName());
        }
        catch (NumberFormatException e) {
            log.error("Invalid jobName format: {}", dto.jobName());
            return false;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        LocalDate bizDate;
        EtlStatistic sta = new EtlStatistic();
        sta.setTid(Long.parseLong(dto.jobName()));
        sta.setStartAt(LocalDateTime.ofEpochSecond(dto.startTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setEndAt(LocalDateTime.ofEpochSecond(dto.endTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setTakeSecs(dto.totalCosts());
        sta.setByteSpeed(dto.byteSpeedPerSecond());
        sta.setRecSpeed(dto.recordSpeedPerSecond());
        sta.setTotalRecs(dto.totalReadRecords());
        sta.setTotalErrors(dto.totalErrorRecords());
        sta.setTotalBytes(dto.byteSpeedPerSecond() * dto.totalCosts());
        try {
            bizDate = LocalDate.ofInstant(sdf.parse(configService.getBizDate()).toInstant(), java.time.ZoneId.systemDefault());
        }
        catch (ParseException e) {
            log.warn("Failed to parse biz date, using start time as biz date");
            bizDate = sta.getStartAt().toLocalDate();
        }
        sta.setBizDate(bizDate);
        return statService.saveOrUpdate(sta);
    }
}
