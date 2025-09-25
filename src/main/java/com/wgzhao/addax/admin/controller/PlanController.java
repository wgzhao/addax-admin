package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpPlan;
import com.wgzhao.addax.admin.service.TbImpPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计划任务配置接口
 */
@Tag(name="计划任务配置接口")
@RestController
@RequestMapping("/plan")
public class PlanController
{

    @Autowired
    private TbImpPlanService tbImpPlanService;

    @GetMapping("")
    public ApiResponse<List<TbImpPlan>> getAllImpPlan() {
        return ApiResponse.success(tbImpPlanService.findAll());
    }

}
