package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpEtlTbls;
import com.wgzhao.fsbrowser.service.ImpEtlTblsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * HIVE的表结构信息 API接口
 *
 * @author 
 */
@Api(value = "/impEtlTbls", tags = {"HIVE的表结构信息API"})
@RequestMapping("/impEtlTbls")
@RestController
public class ImpEtlTblsController {

    @Autowired
    private ImpEtlTblsService impEtlTblsService;

    /**
     * 查询HIVE的表结构信息数据
     *
     * @return List of {@link ImpEtlTbls}
     */
    @ApiOperation(value = "查询HIVE的表结构信息数据", httpMethod = "GET",tags = {"查询HIVE的表结构信息数据"})
    @GetMapping(value = "/list")
    public List<ImpEtlTbls> getList() {
        return impEtlTblsService.findAll();
    }
}