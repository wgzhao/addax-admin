package com.wgzhao.fsbrowser.controller.maintable;

import com.wgzhao.fsbrowser.model.oracle.ImpSpCom;
import com.wgzhao.fsbrowser.model.oracle.VwImpEtl;
import com.wgzhao.fsbrowser.repository.oracle.ImpSpComRepo;
import com.wgzhao.fsbrowser.repository.oracle.ViewPseudoRepo;
import com.wgzhao.fsbrowser.repository.oracle.VwImpEtlRepo;
import com.wgzhao.fsbrowser.service.VwImpEtlService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ODS 采集配置接口
 */
@Api(value = "ODS 采集配置接口", tags = {"主表配置"})
@RestController
@RequestMapping("/maintable/ods")
public class ODSController {

    @Autowired
    private VwImpEtlService  vwImpEtlService;

    @Autowired
    private ViewPseudoRepo viewPseudoRepo;

    @Autowired
    private ImpSpComRepo impSpComRepo;

    // 获得 ODS 采集的基本信息
    @RequestMapping("/list")
    public List<VwImpEtl> getODSList(@RequestParam(value = "flag", defaultValue = "", required = false) String flag,
                                     @RequestParam(value = "q", defaultValue = "", required = false) String filter) {
        return vwImpEtlService.getOdsInfo(flag, filter);
    }

    @RequestMapping("/odsinfo/{tid}")
    public VwImpEtl getODSInfo(@PathVariable("tid") String tid)
    {
        return vwImpEtlService.findOneODSInfo(tid);
    }

    // 字段对比
    @RequestMapping("/fieldCompare/{tid}")
    public List<Map<String, Object>> fieldCompare(@PathVariable("tid") String tid)
    {
        return viewPseudoRepo.findFieldsCompare(tid);
    }

    // 命令列表
    @RequestMapping("/cmdList/{spId}")
    public List<ImpSpCom> cmdList(@PathVariable("spId") String spId)
    {
        return impSpComRepo.findAllBySpId(spId);
    }
}
