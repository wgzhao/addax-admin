package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpPlan;
import com.wgzhao.fsbrowser.service.ImpPlanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_PLAN API接口
 *
 * @author 
 */
@Api(value = "/impPlan", tags = {"TB_IMP_PLANAPI"})
@RequestMapping("/impPlan")
@RestController
public class ImpPlanController {

    @Autowired
    private ImpPlanService impPlanService;

    /**
     * 查询TB_IMP_PLAN数据
     *
     * @return List of {@link ImpPlan}
     */
    @ApiOperation(value = "查询TB_IMP_PLAN数据", httpMethod = "GET",tags = {"查询TB_IMP_PLAN数据"})
    @GetMapping(value = "/list")
    public List<ImpPlan> getList() {
        return impPlanService.findAll();
    }
}