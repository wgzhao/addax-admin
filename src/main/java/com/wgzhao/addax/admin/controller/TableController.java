package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.DbSourceDto;
import com.wgzhao.addax.admin.dto.EtlBatchReq;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwImpEtlWithDb;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.service.StatService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.utils.DsUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 采集表管理接口
 */
@Api(value = "采集表配置管理接口")
@RestController
@RequestMapping("/table")
public class TableController
{

    @Autowired
    private TableService tableService;

    @Autowired
    private EtlTableRepo etlTableRepo;

    @Autowired
    private EtlSourceRepo etlSourceRepo;

    @Autowired
    private StatService statService;

    @Resource
    DsUtil dsUtil;

    // 获得 ODS 采集的基本信息，仅用于列表展示
    @GetMapping
    public ApiResponse<Page<EtlTable>> list(@RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "flag", required = false) String flag,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortOrder", required = false) String sortOrder)
    {
        if (page < 0) {
            page = 0;
        }
        if (pageSize == -1) {
            //means the browser select the "All" option
            pageSize = Integer.MAX_VALUE; // or some large number
        }
        if (flag != null && !flag.isEmpty()) {
            return ApiResponse.success(tableService.getTablesByFlag(page, pageSize, q, flag, sortField, sortOrder));
        }
        else {
            return ApiResponse.success(tableService.getTablesInfo(page, pageSize, q, sortField, sortOrder));
        }
    }

    @GetMapping("/{tid}")
    public ApiResponse<VwImpEtlWithDb> get(@PathVariable("tid") String tid)
    {
        return ApiResponse.success(tableService.findOneODSInfo(tid));
    }

    @DeleteMapping("/{tid}")
    public ApiResponse<String> delete(@PathVariable("tid") long tid)
    {
        CompletableFuture.runAsync(() -> {
            etlTableRepo.deleteById(tid);
        });
        return ApiResponse.success("delete success");
    }

    // 字段对比
    @RequestMapping("/fieldCompare/{tid}")
    public ApiResponse<List<Map<String, Object>>> fieldCompare(@PathVariable("tid") String tid)
    {
        return ApiResponse.success(tableService.findFieldsCompare(tid));
    }


    // 取 Addax 执行结果 按照名称显示最近15条记录
    @RequestMapping("/addaxResult/{tid}")
    public ApiResponse<List<EtlStatistic>> addaxResult(@PathVariable("tid") long tid)
    {
        return ApiResponse.success(statService.getLast15Records(tid));
    }

    // 批量新增表时的源系统下拉框
    @RequestMapping("/sourceSystem")
    public ApiResponse<List<EtlSource>> sysList()
    {
        return ApiResponse.success(etlSourceRepo.findAllByEnabled(true));
    }

    // 单个采集源下的所有数据库
    @PostMapping("/dbSources")
    public ApiResponse<List<String>> dbList(@RequestBody Map<String, String> payload)
    {
        List<String> result = new ArrayList<>();
        try {
            Connection connection = DriverManager.getConnection(payload.get("url"), payload.get("username"), payload.get("password"));
            // get all database names
            ResultSet catalogs = connection.getMetaData().getCatalogs();
            while (catalogs.next()) {
                result.add(catalogs.getString(1));
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ApiResponse.success(result);
    }

    // 获取指定采集源下，指定数据库下的所有还没有采集的表
    @PostMapping("/tables")
    public ApiResponse<List<String>> tableList(@RequestBody Map<String, String> payload)
    {
        List<String> result = new ArrayList<>();
        // get all exists tables
        List<String> existsTables = etlTableRepo.findTables(Integer.parseInt(payload.get("sysId")), payload.get("db"));
        try {
            Connection connection = DriverManager.getConnection(payload.get("url"), payload.get("username"), payload.get("password"));
            connection.setSchema(payload.get("db"));
            // get all tables names
            ResultSet tables = connection.getMetaData().getTables(payload.get("db"), null, "%", new String[] {"TABLE"});
            while (tables.next()) {
                result.add(tables.getString("TABLE_NAME"));
            }
            // result - existsTables
            result.removeAll(existsTables);
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return ApiResponse.success(result);
    }

    // 保存批量增加的表
    @PostMapping("/batchSave")
    public ApiResponse<Integer> saveODS(@RequestBody List<EtlTable> etls)
    {
        etlTableRepo.saveAll(etls);
        return ApiResponse.success(etls.size());
    }

    @PostMapping("/save")
    public ApiResponse<EtlTable> save(@RequestBody EtlTable etl)
    {
        return ApiResponse.success(etlTableRepo.save(etl));
    }

    // 启动采集
    @PostMapping(path = "/startEtl", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<String> startEtl(@RequestBody Map<String, String> payload, HttpServletResponse response)
    {
        Pair<Boolean, String> pair = dsUtil.execDs(payload.getOrDefault("ctype", "sp"), null);
        if (pair.getFirst()) {
            response.setStatus(HttpStatus.OK.value());
        }
        else {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
        return ApiResponse.success(pair.getSecond());
    }

    @PostMapping(path = "/updateSchema")
    public ApiResponse<String> updateSchema()
    {
//        taskService.tableSchemaUpdate();
        boolean result = tableService.addTableInfo();
        if (!result) {
            return ApiResponse.error(500, "schema update failed");
        }
        else {
            return ApiResponse.success("schema update has scheduled");
        }
    }

    /**
     * 更新采集表的某些字段信息
     * payload
     * {
     * tid: [2, 3],
     * flag: "N",
     * retryCnt: 3
     * }
     */

    @PostMapping("/batchUpdateStatusAndFlag")
    public ApiResponse<EtlTable> update(@RequestBody EtlBatchReq payload)
    {
        List<String> tids = payload.getTids();
        String flag = payload.getFlag();
        int retryCnt = payload.getRetryCnt();
        tableService.updateStatusAndFlag(tids, flag, retryCnt);
        return ApiResponse.success(null);
    }
}
