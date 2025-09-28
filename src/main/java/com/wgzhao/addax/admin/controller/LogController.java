package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.dto.AddaxReportDto;
import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.StatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 日志相关接口，主要是根据指定条件，从特定的目录获取特定日志，并进行展示
 */
@RestController
@RequestMapping("/log")
@Slf4j
public class LogController {

    @Autowired
    private AddaxLogService addaxLogService;

    @Autowired
    private StatService statService;

    // 获取指定 SP 的日志列表
    @GetMapping("/{tid}")
    public ApiResponse<List<AddaxLogDto>> getSpLog(@PathVariable("tid") String tid)
    {
        return ApiResponse.success(addaxLogService.getLogEntry(tid));
    }

    // 获取指定日志文件的内容
    @GetMapping("/{id}/content")
    public ResponseEntity<String> getLogFileContent(@PathVariable("id") Long id)
    {
        String addaxLog = addaxLogService.getLogContent(id);
        return ResponseEntity.ok(addaxLog);
    }


    @PostMapping(value = "/job-report", consumes = "application/json")
    public boolean jobReport(@RequestBody AddaxReportDto dto) {
        log.info("job report: {}", dto);
        EtlStatistic sta = new EtlStatistic();
        sta.setTid(Long.parseLong(dto.getJobName()));
        sta.setStartAt(LocalDateTime.ofEpochSecond(dto.getStartTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setEndAt( LocalDateTime.ofEpochSecond(dto.getEndTimeStamp(), 0, java.time.ZoneOffset.ofHours(8)));
        sta.setTakeSecs(dto.getTotalCosts());
        sta.setByteSpeed(dto.getByteSpeedPerSecond());
        sta.setRecSpeed(dto.getRecordSpeedPerSecond());
        sta.setTotalRecs(dto.getTotalReadRecords());
        sta.setTotalErrors(dto.getTotalErrorRecords());
        sta.setRunDate(sta.getStartAt().toLocalDate());
        sta.setTotalBytes(dto.getByteSpeedPerSecond() * dto.getTotalCosts());
        return statService.saveOrUpdate(sta);
    }
}
