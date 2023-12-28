package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.repository.pg.TbImpChkEtlRepo;
import com.wgzhao.fsbrowser.utils.CacheUtil;
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
    public List<Map<String, Object>> abnormalRecord() {
        String td = cacheUtil.get("param.TD");
        String ltd = cacheUtil.get("param.LTD");
        return tbImpChkEtlRepo.findAbnormalRecord(td, ltd);
    }
}
