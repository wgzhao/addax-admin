package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.VwImpSystem;
import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import com.wgzhao.fsbrowser.repository.oracle.VwImpSystemRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统信息接口
 */
@Api(value = "系统信息接口", tags = {"系统信息接口"})
@RestController
@RequestMapping("/systemInfo")
public class SystemInfoController {

    @Autowired
    private VwImpSystemRepo vwImpSystemRepo;

    @Autowired
    private ViewPseudoRepo viewPseudoRepo;

    // 数据中心采集及数据服务系统清单
    @GetMapping("/etlAndDs")
    public List<Map<String, Object>> etlAndDs(@RequestParam(required = false, name="q") String filter) {
        if (filter != null && !filter.isEmpty()) {
            return viewPseudoRepo.findEtlAndDs(filter);
        } else {
            return viewPseudoRepo.findEtlAndDs();
        }
    }

    // 数据中心采集表清单(显示100条)
    @GetMapping("/etlInfo")
    public List<Map<String, Object>> etlInfo(@RequestParam(required = false, name="q") String filter) {
        if (filter != null && !filter.isEmpty()) {
            return viewPseudoRepo.findTop100EtlInfo(filter);
        } else {
            return viewPseudoRepo.findTop100EtlInfo();
        }
    }

    // 数据中心数据推送表清单(显示100条)
    @GetMapping("/dsInfo")
    public List<Map<String, Object>> dsInfo(@RequestParam(required = false, name="q") String filter) {
        if (filter != null && !filter.isEmpty()) {
            return viewPseudoRepo.findTop100DsInfo(filter);
        } else {
            return viewPseudoRepo.findTop100DsInfo();
        }
    }
}
