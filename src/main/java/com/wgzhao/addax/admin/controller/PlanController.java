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
 * 计划任务配置接口，提供计划任务相关的查询功能
 */
@Tag(name="计划任务配置接口")
@RestController
@RequestMapping("/plan")
public class PlanController
{
    /** 计划任务服务，用于处理计划任务相关业务逻辑 */
    @Autowired
    private TbImpPlanService tbImpPlanService;

    /**
     * 查询所有计划任务
     * @return 所有计划任务列表
     */
    @GetMapping("")
    public ApiResponse<List<TbImpPlan>> getAllImpPlan() {
        return ApiResponse.success(tbImpPlanService.findAll());
    }
}
