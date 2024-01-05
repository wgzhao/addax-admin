package com.wgzhao.fsbrowser.controller.maintable;

import com.wgzhao.fsbrowser.model.oracle.TbImpPlan;
import com.wgzhao.fsbrowser.service.TbImpPlanService;
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
@RequestMapping("/maintable/plantask")
public class PlanTaskController
{

    @Autowired
    private TbImpPlanService tbImpPlanService;

    @GetMapping({"/list", "/"})
    public List<TbImpPlan> getAllImpPlan() {
        return tbImpPlanService.findAll();
    }

}
