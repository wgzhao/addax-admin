package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
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

    // 各数据源采集完成率，用于图表展示
    @RequestMapping("/accomplishRatio")
    public List<Map<String, Float>> accompListRatio() {
        return viewPseudoRepo.accompListRatio();
    }
}
