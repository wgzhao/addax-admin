package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpEtl;
import com.wgzhao.fsbrowser.service.ImpEtlService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_ETL API接口
 *
 * @author 
 */
@Api(value = "/impEtl", tags = {"TB_IMP_ETLAPI"})
@RequestMapping("/impEtl")
@RestController
public class ImpEtlController {

    @Autowired
    private ImpEtlService impEtlService;

    /**
     * 查询TB_IMP_ETL数据
     *
     * @return List of {@link ImpEtl}
     */
    @ApiOperation(value = "查询TB_IMP_ETL数据", httpMethod = "GET",tags = {"查询TB_IMP_ETL数据"})
    @GetMapping(value = "/list")
    public List<ImpEtl> getList() {
        return impEtlService.findAll();
    }
}