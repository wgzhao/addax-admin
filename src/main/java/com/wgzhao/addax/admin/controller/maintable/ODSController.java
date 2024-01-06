package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.ImpSpCom;
import com.wgzhao.addax.admin.model.oracle.TbImpSpNeedtab;
import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import com.wgzhao.addax.admin.model.pg.VwAddaxLog;
import com.wgzhao.addax.admin.repository.oracle.ImpSpComRepo;
import com.wgzhao.addax.admin.repository.oracle.ViewPseudoRepo;
import com.wgzhao.addax.admin.service.TbImpSpNeedtabService;
import com.wgzhao.addax.admin.service.VwAddaxLogService;
import com.wgzhao.addax.admin.service.VwImpEtlService;
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

    @Autowired
    private TbImpSpNeedtabService tbImpSpNeedtabService;

    @Autowired
    private VwAddaxLogService vwAddaxLogService;

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

    // 表使用场景
    @RequestMapping("/tableUsed")
    public List<TbImpSpNeedtab> tableUsed(@RequestParam("tablename") String tablename,
                                          @RequestParam("sysId") String sysId)
    {
        return tbImpSpNeedtabService.getNeedtablesByTablename(tablename, sysId);
    }

    // 取 Addax 执行结果 按照名称显示最近15条记录
    @RequestMapping("/addaxResult/{spname}")
    public List<VwAddaxLog> addaxResult(@PathVariable("spname") String spname)
    {
        List<String> spNames = List.of(spname, spname +"_100", spname + "_102");

        return vwAddaxLogService.getAddaxResult(spNames);
    }
}
