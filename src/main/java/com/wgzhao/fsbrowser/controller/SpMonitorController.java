package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
/**
 * SP 监控
 */
@RestController
@CrossOrigin
@RequestMapping("/spMonitor")
public class SpMonitorController {

    @Autowired
    private ViewPseudoRepo  viewPseudoRepo;
    // SP 整体执行情况
    @GetMapping("/spTotalExec")
    public List<Map<String, Object>> spTotalExec() {
        return viewPseudoRepo.findSpExecInfo();
    }
}
