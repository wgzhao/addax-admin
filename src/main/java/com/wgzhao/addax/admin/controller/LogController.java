package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.utils.CacheUtil;
import com.wgzhao.addax.admin.utils.LogFileUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志相关接口，主要是根据指定条件，从特定的目录获取特定日志，并进行展示
 */
@RestController
@RequestMapping("/log")
public class LogController {

    @Resource
    CacheUtil cacheUtil;

    @Resource
    LogFileUtil logFileUtil;

    // 获取指定 SP 的日志列表
    @GetMapping("/logFiles/{spName}")
    public ApiResponse<List<String>> getSpLog(@PathVariable("spName") String spName)
    {
        String tradeRange = cacheUtil.get("param.L5TD") + "," + cacheUtil.get("param.NTD");

        List<String> result = new ArrayList<>(logFileUtil.getFs(tradeRange, spName));
        result.addAll(logFileUtil.getFs(tradeRange, "tuna_sp_etl_" + spName));
        return ApiResponse.success(result);
    }

    // 获取指定日志文件的内容
    @GetMapping("/logFileContent")
    public ApiResponse<String> getLogFileContent(@RequestParam("f") String fname)
    {
        return ApiResponse.success(logFileUtil.getFileContent(fname));
    }
}
