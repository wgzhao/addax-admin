package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpJour;
import com.wgzhao.fsbrowser.service.ImpJourService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_JOUR API接口
 *
 * @author 
 */
@Api(value = "/impJour", tags = {"TB_IMP_JOURAPI"})
@RequestMapping("/impJour")
@RestController
public class ImpJourController {

    @Autowired
    private ImpJourService impJourService;

    /**
     * 查询TB_IMP_JOUR数据
     *
     * @return List of {@link ImpJour}
     */
    @ApiOperation(value = "查询TB_IMP_JOUR数据", httpMethod = "GET",tags = {"查询TB_IMP_JOUR数据"})
    @GetMapping(value = "/list")
    public List<ImpJour> getList() {
        return impJourService.findAll();
    }
}