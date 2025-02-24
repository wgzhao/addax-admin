package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.repository.pg.TbImpChkEtlRepo;
import com.wgzhao.addax.admin.utils.CacheUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 盘后检查接口
 */

@Api(value = "盘后检查接口", tags = {"盘后检查接口"})
@RestController
@RequestMapping("/closeCheck")
public class CloseCheckController {

    @Autowired
    private TbImpChkEtlRepo tbImpChkEtlRepo;

    @Resource
    CacheUtil cacheUtil;

    // 采集表记录数异常
    @GetMapping("/abnormalRecord")
    public ApiResponse<List<Map<String, Object>>> abnormalRecord() {
        String td = cacheUtil.get("param.TD");
        String ltd = cacheUtil.get("param.LTD");
        return ApiResponse.success(tbImpChkEtlRepo.findAbnormalRecord(td, ltd));
    }
}
