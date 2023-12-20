package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpEtlSoutab;
import com.wgzhao.fsbrowser.service.ImpEtlSoutabService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_ETL_SOUTAB API接口
 *
 * @author 
 */
@Api(value = "/impEtlSoutab", tags = {"TB_IMP_ETL_SOUTABAPI"})
@RequestMapping("/impEtlSoutab")
@RestController
public class ImpEtlSoutabController {

    @Autowired
    private ImpEtlSoutabService impEtlSoutabService;

    /**
     * 查询TB_IMP_ETL_SOUTAB数据
     *
     * @return List of {@link ImpEtlSoutab}
     */
    @ApiOperation(value = "查询TB_IMP_ETL_SOUTAB数据", httpMethod = "GET",tags = {"查询TB_IMP_ETL_SOUTAB数据"})
    @GetMapping(value = "/list")
    public List<ImpEtlSoutab> getList() {
        return impEtlSoutabService.findAll();
    }
}