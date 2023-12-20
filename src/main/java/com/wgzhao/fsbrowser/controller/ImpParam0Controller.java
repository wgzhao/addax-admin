package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpParam0;
import com.wgzhao.fsbrowser.service.ImpParam0Service;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * 日期参数文件基表 API接口
 *
 * @author 
 */
@Api(value = "/impParam0", tags = {"日期参数文件基表API"})
@RequestMapping("/impParam0")
@RestController
public class ImpParam0Controller {

    @Autowired
    private ImpParam0Service impParam0Service;

    /**
     * 查询日期参数文件基表数据
     *
     * @return List of {@link ImpParam0}
     */
    @ApiOperation(value = "查询日期参数文件基表数据", httpMethod = "GET",tags = {"查询日期参数文件基表数据"})
    @GetMapping(value = "/list")
    public List<ImpParam0> getList() {
        return impParam0Service.findAll();
    }
}