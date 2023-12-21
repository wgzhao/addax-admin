package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpSp;
import com.wgzhao.fsbrowser.service.ImpSpService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

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
     * @return List of {@link ImpSp}
     */
    @ApiOperation(value = "查询HADOOP_SP的配置主表数据", httpMethod = "GET",tags = {"查询HADOOP_SP的配置主表数据"})
    @GetMapping(value = "/list")
    public List<ImpSp> getList() {
        return impSpService.findAll();
    }
}