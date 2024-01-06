package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.oracle.TbImpSp;
import com.wgzhao.addax.admin.service.ImpSpService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HADOOP_SP的配置主表 API接口
 *
 * @author 
 */
@Api(value = "/impSp", tags = {"HADOOP_SP的配置主表API"})
@RequestMapping("/impSp")
@RestController
@CrossOrigin
public class ImpSpController {

    @Autowired
    private ImpSpService impSpService;

    /**
     * 查询HADOOP_SP的配置主表数据
     *
     * @return List of {@link com.wgzhao.addax.admin.model.oracle.TbImpSp}
     */
    @ApiOperation(value = "查询HADOOP_SP的配置主表数据", httpMethod = "GET",tags = {"查询HADOOP_SP的配置主表数据"})
    @GetMapping(value = "/list")
    public List<TbImpSp> getList() {
        return impSpService.findAll();
    }

    /**
     * 生成指定 sp_id 的溯源数据
     */
    @GetMapping(value="/lineage/{id}")
    public List<Map<String, Object>> getLineage(@PathVariable("id") String spId) {
        return impSpService.findLineage(spId);
    }

    /**
     * SP 前置情况
     */
    @GetMapping(value="/prequires/{id}")
    public List<Map<String, String>> getPrequires(@PathVariable("id") String spId) {
        return impSpService.findRequires(spId);
    }

    // 找个SP表的上下游关系
    @GetMapping(value="/through/{id}")
    public Map<String, String> getThrough(@PathVariable("id") String spId) {
        return impSpService.findThrough(spId);
    }
}