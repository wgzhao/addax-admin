package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.VwImpEtlWithDb;
import com.wgzhao.addax.admin.repository.ViewPseudoRepo;
import com.wgzhao.addax.admin.service.EtlService;
import com.wgzhao.addax.admin.model.VwImpSystem;
import com.wgzhao.addax.admin.service.SystemService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
    private ViewPseudoRepo viewPseudoRepo;

    @Autowired
    private SystemService systemService;

    @Autowired
    private EtlService etlService;

    // 数据中心采集及数据服务系统清单
    @GetMapping("/etlAndDs")
    public ApiResponse<List<VwImpSystem>> etlAndDs(@RequestParam(required = false, name="q") String filter) {
        return ApiResponse.success(systemService.fetchEtlDSInfo(filter));
    }

    // 数据中心采集表清单(显示100条)
    @GetMapping("/etlInfo")
    public ApiResponse<Page<VwImpEtlWithDb>> etlInfo(@RequestParam(name="page", defaultValue = "1") int page,
                                  @RequestParam(name="pageSize", defaultValue = "10") int pageSize) {
        return ApiResponse.success(etlService.fetchEtlInfo(page, pageSize));
    }

    // 数据中心数据推送表清单(显示100条)
    @GetMapping("/dsInfo")
    public ApiResponse<List<Map<String, Object>>> dsInfo(@RequestParam(required = false, name="q") String filter) {
        if (filter != null && !filter.isEmpty()) {
            return ApiResponse.success(viewPseudoRepo.findTop100DsInfo(filter));
        } else {
            return ApiResponse.success(viewPseudoRepo.findTop100DsInfo());
        }
    }
}
