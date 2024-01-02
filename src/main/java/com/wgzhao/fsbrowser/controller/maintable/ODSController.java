package com.wgzhao.fsbrowser.controller.maintable;

import com.wgzhao.fsbrowser.model.oracle.VwImpEtl;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlRepo;
import com.wgzhao.fsbrowser.service.VwImpEtlService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ODS 采集配置接口
 */
@Api(value = "ODS 采集配置接口", tags = {"主表配置"})
@RestController
@RequestMapping("/maintable/ods")
public class ODSController {

    @Autowired
    private VwImpEtlService  vwImpEtlService;
    // 获得 ODS 采集的基本信息
    @RequestMapping("/list")
    public List<VwImpEtl> getODSList(@RequestParam(value = "flag", defaultValue = "", required = false) String flag,
                                     @RequestParam(value = "q", defaultValue = "", required = false) String filter) {
        return vwImpEtlService.getOdsInfo(flag, filter);
    }
}
