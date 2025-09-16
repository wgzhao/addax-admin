package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.repository.AddaxLogRepo;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.utils.CacheUtil;
import com.wgzhao.addax.admin.utils.LogFileUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 日志相关接口，主要是根据指定条件，从特定的目录获取特定日志，并进行展示
 */
@RestController
@RequestMapping("/log")
public class LogController {

    @Autowired
    private AddaxLogService addaxLogService;

    // 获取指定 SP 的日志列表
    @GetMapping("/addaxLog/list/{tid}")
    public ApiResponse<List<AddaxLogDto>> getSpLog(@PathVariable("tid") String tid)
    {
        return ApiResponse.success(addaxLogService.getLogEntry(tid));
    }

    // 获取指定日志文件的内容
    @GetMapping("/addaxLog/content/{id}")
    public ApiResponse<String> getLogFileContent(@PathVariable("id") Long id)
    {
        Optional<AddaxLog> addaxLog = addaxLogService.getLogContent(id);
        return addaxLog.map(log -> ApiResponse.success(log.getLog())).orElseGet(() -> ApiResponse.error(400, "未找到对应日志"));
    }

}
