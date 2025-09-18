package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpPlan;
import com.wgzhao.addax.admin.service.TbImpPlanService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计划任务配置接口
 */
@Api(value="计划任务配置接口", tags = {"主表配置"})
@RestController
@RequestMapping("/plantask")
public class PlanTaskController
{

    @Autowired
    private TbImpPlanService tbImpPlanService;

    @GetMapping({"/list", "/"})
    public ApiResponse<List<TbImpPlan>> getAllImpPlan() {
        return ApiResponse.success(tbImpPlanService.findAll());
    }

}
