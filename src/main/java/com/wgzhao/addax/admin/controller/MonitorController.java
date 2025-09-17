package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpJour;
import com.wgzhao.addax.admin.model.TbImpChkSpEntity;
import com.wgzhao.addax.admin.repository.ViewPseudoRepo;
import com.wgzhao.addax.admin.repository.TbImpChkSpRepo;
import com.wgzhao.addax.admin.service.TbImpJourService;
import com.wgzhao.addax.admin.utils.CacheUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.wgzhao.addax.admin.utils.TradeDateUtils.calcTradeDate;
/**
 * 监控
 */
@RestController
@CrossOrigin
@RequestMapping("/sp")
public class MonitorController
{

    @Autowired
    private ViewPseudoRepo  viewPseudoRepo;

    @Autowired
    private TbImpChkSpRepo tbImpChkSpRepo;

    @Autowired
    private TbImpJourService tbImpJourService;

    @Resource
    CacheUtil cacheUtil;

    // SP 整体执行情况
    @GetMapping("/totalExec")
    public ApiResponse<List<Map<String, Object>>> spTotalExec() {
        return ApiResponse.success(viewPseudoRepo.findSpExecInfo());
    }

    // SP 计算的有效性检测结果
    @GetMapping("/validChkSp")
    public ApiResponse<List<TbImpChkSpEntity>> getValidChkSp() {
        return ApiResponse.success(tbImpChkSpRepo.findValidChkSp(calcTradeDate(5, "yyyyMMdd")));
    }

    // SP计算的记录数检测结果
    @GetMapping("/validSpCnt")
    public ApiResponse<List<Map<String, Object>>> getValidSpCnt() {
        return ApiResponse.success(tbImpChkSpRepo.findValidSpCnt(calcTradeDate(5, "yyyyMMdd")));
    }

    // 特殊任务：报错、重跑
    @GetMapping("/errorTasks")
    public ApiResponse<List<Map<String, Object>>> getErrorTasks() {
        return ApiResponse.success(viewPseudoRepo.findErrorTasks());
    }

    // SP计算相关流水
    @GetMapping("/pipeline")
    public ApiResponse<List<TbImpJour>> getPipeline() {
        return ApiResponse.success(tbImpJourService.findPipeline(cacheUtil.get("param.TD")));
    }
}
