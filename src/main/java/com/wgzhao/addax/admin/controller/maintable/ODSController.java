package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.oracle.TbImpEtl;
import com.wgzhao.addax.admin.model.oracle.ImpSpCom;
import com.wgzhao.addax.admin.model.oracle.TbImpSpNeedtab;
import com.wgzhao.addax.admin.model.oracle.VwImpEtl;
import com.wgzhao.addax.admin.model.pg.VwAddaxLog;
import com.wgzhao.addax.admin.repository.oracle.ImpSpComRepo;
import com.wgzhao.addax.admin.repository.oracle.TbImpDBRepo;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
    private VwImpEtlService vwImpEtlService;

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

    @Autowired
    private TbImpDBRepo tbImpDBRepo;

    @Resource
    DsUtil dsUtil;

    // 获得 ODS 采集的基本信息，仅用于列表展示
    @GetMapping
    public ApiResponse<Page<VwImpEtl>> list(@RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "flag", required = false) String flag) {
        if (page < 0) page = 0;
        if (flag != null && !flag.isEmpty()) {
            return ApiResponse.success(vwImpEtlService.getOdsByFlag(page, pageSize, q, flag));
        } else {
            return ApiResponse.success(vwImpEtlService.getOdsInfo(page, pageSize, q));
        }
    }

    @GetMapping("/{tid}")
    public ApiResponse<VwImpEtl> get(@PathVariable("tid") String tid) {
        return ApiResponse.success(vwImpEtlService.findOneODSInfo(tid));
    }

    // 字段对比
    @RequestMapping("/fieldCompare/{tid}")
    public ApiResponse<List<Map<String, Object>>> fieldCompare(@PathVariable("tid") String tid) {
        return ApiResponse.success(viewPseudoRepo.findFieldsCompare(tid));
    }

    // 命令列表
    @RequestMapping("/cmdList/{spId}")
    public ApiResponse<List<ImpSpCom>> cmdList(@PathVariable("spId") String spId) {
        return ApiResponse.success(impSpComRepo.findAllBySpId(spId));
    }

    // 表使用场景
    @RequestMapping("/tableUsed")
    public ApiResponse<List<TbImpSpNeedtab>> tableUsed(@RequestParam("tablename") String tablename,
            @RequestParam("sysId") String sysId) {
        return ApiResponse.success(tbImpSpNeedtabService.getNeedtablesByTablename(tablename, sysId));
    }

    // 取 Addax 执行结果 按照名称显示最近15条记录
    @RequestMapping("/addaxResult/{spname}")
    public ApiResponse<List<VwAddaxLog>> addaxResult(@PathVariable("spname") String spname) {
        List<String> spNames = List.of(spname, spname + "_100", spname + "_102");
        return ApiResponse.success(vwAddaxLogService.getAddaxResult(spNames));
    }

    // 批量新增表时的源系统下拉框
    @RequestMapping("/sourceSystem")
    public ApiResponse<List<Map<String, String>>> sysList() {
        return ApiResponse.success(viewPseudoRepo.findSourceSystem());
    }

    // 单个采集源下的所有数据库
    @PostMapping("/dbSources")
    public ApiResponse<List<String>> dbList(@RequestBody Map<String, String> payload) {
        List<String> result = new ArrayList<>();
        try {
            Connection connection = DriverManager.getConnection(payload.get("url"), payload.get("username"), payload.get("password"));
            // get all database names
            ResultSet catalogs = connection.getMetaData().getCatalogs();
            while (catalogs.next()) {
                result.add(catalogs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ApiResponse.success(result);
    }

    // 获取指定采集源下，指定数据库下的所有还没有采集的表
    @PostMapping("/tables")
    public ApiResponse<List<String>> tableList(@RequestBody Map<String, String> payload) {
        List<String> result = new ArrayList<>();
        // get all exists tables
        List<String> existsTables = tbImpEtlRepo.findTables(payload.get("sysId"), payload.get("db"));
        try {
            Connection connection = DriverManager.getConnection(payload.get("url"), payload.get("username"), payload.get("password"));
            connection.setSchema(payload.get("db"));
            // get all tables names
            ResultSet tables = connection.getMetaData().getTables(payload.get("db"), null, "%", new String[]{"TABLE"});
            while (tables.next()) {
                result.add(tables.getString("TABLE_NAME"));
            }
            // result - existsTables
            result.removeAll(existsTables);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ApiResponse.success(result);
    }

    // 保存批量增加的表
    @PostMapping("/batchSave")
    public ApiResponse<Integer> saveODS(@RequestBody List<TbImpEtl> etls) {
        tbImpEtlRepo.saveAll(etls);
        return ApiResponse.success(etls.size());
    }

    @PostMapping("/save")
    public ApiResponse<TbImpEtl> save(@RequestBody TbImpEtl etl) {
        return ApiResponse.success(tbImpEtlRepo.save(etl));
    }

    // 启动采集
    @PostMapping(path = "/startEtl", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> startEtl(@RequestBody Map<String, String> payload, HttpServletResponse response) {
        Pair<Boolean, String> pair = dsUtil.execDs(payload.getOrDefault("ctype", "sp"), null);
        if (pair.get1st()) {
            response.setStatus(HttpStatus.OK.value());
        } else {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return ApiResponse.success(pair.get2nd());
    }
}
