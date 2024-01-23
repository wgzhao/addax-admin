package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.TbImpEtl;
import com.wgzhao.addax.admin.model.oracle.ImpSpCom;
import com.wgzhao.addax.admin.model.oracle.TbImpSpNeedtab;
import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import com.wgzhao.addax.admin.model.pg.VwAddaxLog;
import com.wgzhao.addax.admin.repository.oracle.ImpSpComRepo;
import com.wgzhao.addax.admin.repository.oracle.TbImpEtlRepo;
import com.wgzhao.addax.admin.repository.oracle.ViewPseudoRepo;
import com.wgzhao.addax.admin.service.TbImpSpNeedtabService;
import com.wgzhao.addax.admin.service.VwAddaxLogService;
import com.wgzhao.addax.admin.service.VwImpEtlService;
import com.wgzhao.addax.admin.utils.DsUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import oracle.ucp.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.http.HttpResponse;
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

    @Autowired
    private TbImpEtlRepo tbImpEtlRepo;

    @Resource
    DsUtil dsUtil;

    // 获得 ODS 采集的基本信息
    @RequestMapping("/list")
    public Page<VwImpEtl> getODSList(@RequestParam(value="page", defaultValue = "0", required = true) int page,
                                     @RequestParam(value="pageSize", defaultValue = "10", required = true) int pageSize,
                                     @RequestParam(value="q", required = false) String q
                                     ) {
        if (page < 0) page = 0;
        return vwImpEtlService.getOdsInfo(page, pageSize, q);
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

    // 批量新增表时的源系统下拉框
    @RequestMapping("/sourceSystem")
    public List<Map<String, String>> sysList()
    {
        return viewPseudoRepo.findSourceSystem();
    }

    // 保存批量增加的表
    @PostMapping("/batchSave")
    public void saveODS(@RequestBody  List<TbImpEtl> etls)
    {
        tbImpEtlRepo.saveAll(etls);
    }

    @PostMapping("/save")
    public TbImpEtl save(@RequestBody TbImpEtl etl)
    {
        return tbImpEtlRepo.save(etl);
    }
    // 启动采集
    @PostMapping(path="/startEtl", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String startEtl(@RequestBody Map<String, String> payload, HttpServletResponse response)
    {
        Pair<Boolean, String> pair = dsUtil.execDs(payload.getOrDefault("ctype", "sp"), null);
        if (pair.get1st()) {
            response.setStatus(HttpStatus.OK.value());
        } else {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return pair.get2nd();
    }
}
