package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.repository.oracle.TbImpFlagRepo;
import com.wgzhao.addax.admin.repository.oracle.ViewPseudoRepo;
import com.wgzhao.addax.admin.utils.CacheUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 监控主页面，主要是汇总信息和图表展示
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private ViewPseudoRepo viewPseudoRepo;

    @Autowired
    private TbImpFlagRepo tbImpFlagRepo;

    @Resource
    CacheUtil cacheUtil;

    // 各数据源采集完成率，用于图表展示
    @RequestMapping("/accomplishRatio")
    public List<Map<String, Float>> accompListRatio() {
        return viewPseudoRepo.accompListRatio();
    }

    //  最近5天采集耗时对比
    @RequestMapping("/last5DaysEtlTime")
    public List<Map<String, Object>> last5DaysEtlTime() {
        int l5td = Integer.parseInt(cacheUtil.get("param.L5TD"));
        return tbImpFlagRepo.findLast5DaysEtlTime(l5td);
    }
}
