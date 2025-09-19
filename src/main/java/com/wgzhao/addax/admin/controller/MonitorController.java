package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbAddaxStatistic;
import com.wgzhao.addax.admin.model.TbImpEtl;
import com.wgzhao.addax.admin.service.AddaxStatService;
import com.wgzhao.addax.admin.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
public class MonitorController
{
    @Autowired
    private TaskService taskService;

    @Autowired
    private
    AddaxStatService addaxStatService;

    // 数据源采集完成情况列表
    @RequestMapping("/accomplishList")
    public ApiResponse<List<Map<String, Object>>> getAll()
    {
        return ApiResponse.success(addaxStatService.statLastAccompRatio());
    }

    // 特殊任务提醒
    @GetMapping("/specialTask")
    public ApiResponse<List<TbImpEtl>> specialTask()
    {
        return ApiResponse.success(taskService.findAllSpecialTask());
    }

    // 任务拒绝行
    @GetMapping("/rejectTask")
    public ApiResponse<List<TbAddaxStatistic>> getTaskReject()
    {
        return ApiResponse.success(addaxStatService.findErrorTask());
    }
}
