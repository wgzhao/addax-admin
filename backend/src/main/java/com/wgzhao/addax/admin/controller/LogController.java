package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.dto.AddaxReportDto;
import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.StatService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志相关接口，主要用于获取采集日志和作业报告
 */
@RestController
@RequestMapping("/log")
@Slf4j
@AllArgsConstructor
public class LogController {

    private final AddaxLogService addaxLogService;
    private final StatService statService;

    /**
     * 获取指定采集任务的日志列表
     * @param tid 采集任务ID
     * @return 日志列表
     */
    @GetMapping("/{tid}")
    public ApiResponse<List<AddaxLogDto>> getSpLog(@PathVariable("tid") String tid)
    {
        return ApiResponse.success(addaxLogService.getLogEntry(tid));
    }

    /**
     * 获取指定日志文件的内容
     * @param id 日志ID
     * @return 日志内容
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<String> getLogFileContent(@PathVariable("id") Long id)
    {
        String addaxLog = addaxLogService.getLogContent(id);
        return ResponseEntity.ok(addaxLog);
    }

    /**
     * 作业报告接口
     * @param dto 作业报告数据
     * @return 是否成功
     */
    @PostMapping(value = "/job-report", consumes = "application/json")
    public boolean jobReport(@RequestBody AddaxReportDto dto) {
        log.info("job report: {}", dto);
        EtlStatistic sta = new EtlStatistic();
        sta.setTid(Long.parseLong(dto.jobName()));
        sta.setStartAt(LocalDateTime.ofEpochSecond(dto.startTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setEndAt( LocalDateTime.ofEpochSecond(dto.endTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setTakeSecs(dto.totalCosts());
        sta.setByteSpeed(dto.byteSpeedPerSecond());
        sta.setRecSpeed(dto.recordSpeedPerSecond());
        sta.setTotalRecs(dto.totalReadRecords());
        sta.setTotalErrors(dto.totalErrorRecords());
        sta.setRunDate(sta.getStartAt().toLocalDate());
        sta.setTotalBytes(dto.byteSpeedPerSecond() * dto.totalCosts());
        return statService.saveOrUpdate(sta);
    }
}
