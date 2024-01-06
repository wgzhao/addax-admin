package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.oracle.TbImpJour;
import com.wgzhao.addax.admin.model.pg.TbImpChkSpEntity;
import com.wgzhao.addax.admin.repository.oracle.ViewPseudoRepo;
import com.wgzhao.addax.admin.repository.pg.TbImpChkSpRepo;
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
 * SP 监控
 */
@RestController
@CrossOrigin
@RequestMapping("/sp")
public class SpMonitorController {

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
    public List<Map<String, Object>> spTotalExec() {
        return viewPseudoRepo.findSpExecInfo();
    }

    // SP 计算的有效性检测结果
    @GetMapping("/validChkSp")
    public List<TbImpChkSpEntity> getValidChkSp() {
        return tbImpChkSpRepo.findValidChkSp(calcTradeDate(5, "yyyyMMdd"));
    }

    // SP计算的记录数检测结果
    @GetMapping("/validSpCnt")
    public List<Map<String, Object>> getValidSpCnt() {
        return tbImpChkSpRepo.findValidSpCnt(calcTradeDate(5, "yyyyMMdd"));
    }

    // 特殊任务：报错、重跑
    @GetMapping("/errorTasks")
    public List<Map<String, Object>> getErrorTasks() {
        return viewPseudoRepo.findErrorTasks();
    }

    // SP计算相关流水
    @GetMapping("/pipeline")
    public List<TbImpJour> getPipeline() {
        return tbImpJourService.findPipeline(cacheUtil.get("param.TD"));
    }
}
