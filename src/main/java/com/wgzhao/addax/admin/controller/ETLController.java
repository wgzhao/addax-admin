package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.EtlTask;
import com.wgzhao.addax.admin.model.VwImpEtlOverprec;
import com.wgzhao.addax.admin.model.TbAddaxSta;
import com.wgzhao.addax.admin.repository.ViewPseudoRepo;
import com.wgzhao.addax.admin.repository.VwImpEtlOverprecRepo;
import com.wgzhao.addax.admin.repository.AddaxStaRepo;
import com.wgzhao.addax.admin.service.EtlTaskEntryService;
import com.wgzhao.addax.admin.service.EtlTaskQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * ETL 采集接口
 */
@RestController
@RequestMapping("/etl")
@CrossOrigin
@Slf4j
public class ETLController
{

    @Autowired
    private VwImpEtlOverprecRepo impEtlOverprecRepo;

    @Autowired
    private ViewPseudoRepo viewPseudoRepo;

    @Autowired
    private AddaxStaRepo addaxStaRepo;

    @Autowired
    private EtlTaskEntryService etlTaskEntryService;

    @Autowired
    private EtlTaskQueueManager queueManager;

    // 数据源采集完成情况列表
    @RequestMapping("/accomplishList")
    public ApiResponse<List<VwImpEtlOverprec>> getAll()
    {
        return ApiResponse.success(impEtlOverprecRepo.findAll());
    }

    // 各数据源采集完成率，用于图表展示
    @RequestMapping("/accomplishRatio")
    public ApiResponse<List<Map<String, Float>>> accompListRatio()
    {
        return ApiResponse.success(viewPseudoRepo.accompListRatio());
    }

    // 日间实时采集任务
    @GetMapping("/realtimeTask")
    public ApiResponse<List<Map<String, Object>>> realtimeTask()
    {
        return ApiResponse.success(viewPseudoRepo.findRealtimeTask());
    }

    // 特殊任务提醒
    @GetMapping("/specialTask")
    public ApiResponse<List<Map<String, Object>>> specialTask()
    {
        return ApiResponse.success(viewPseudoRepo.findAllSpecialTask());
    }

    // 任务拒绝行
    @GetMapping("/rejectTask")
    public ApiResponse<List<TbAddaxSta>> getTaskReject()
    {
        return ApiResponse.success(addaxStaRepo.findByTotalErrNot(0));
    }

    /**
     * 启动采集任务入口 - 扫描数据库并加入队列
     */
    @PostMapping("/start")
    public Map<String, Object> startEtlTasks()
    {
        log.info("接收到启动采集任务的请求");
        String result = etlTaskEntryService.executePlanStartWithQueue();

        return Map.of(
                "success", true,
                "message", result,
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 获取队列状态
     */
    @GetMapping("/status")
    public Map<String, Object> getQueueStatus()
    {
        return etlTaskEntryService.getEtlQueueStatus();
    }

    /**
     * 手动添加任务到队列
     */
    @PostMapping("/add/{etlId}")
    public Map<String, Object> addTaskToQueue(@PathVariable String etlId)
    {
        boolean success = etlTaskEntryService.addEtlTaskToQueue(etlId);

        return Map.of(
                "success", success,
                "message", success ? "任务已加入队列" : "添加任务失败",
                "etlId", etlId
        );
    }

    /**
     * 停止队列监控
     */
    @PostMapping("/stop")
    public Map<String, Object> stopQueueMonitor()
    {
        String result = etlTaskEntryService.stopQueueMonitor();
        return Map.of("success", true, "message", result);
    }

    /**
     * 重启队列监控
     */
    @PostMapping("/restart")
    public Map<String, Object> restartQueueMonitor()
    {
        String result = etlTaskEntryService.restartQueueMonitor();
        return Map.of("success", true, "message", result);
    }

    /**
     * 重置队列
     */
    @PostMapping("/reset")
    public Map<String, Object> resetQueue()
    {
        String result = etlTaskEntryService.resetQueue();
        return Map.of("success", true, "message", result);
    }

    @PostMapping("/updateJob")
    public Map<String, Object> updateJob()
    {
        etlTaskEntryService.updateJob(null);
        return Map.of("success", true, "message", "success");
    }

    @PostMapping("/updateJob/{tid}")
    public Map<String, Object> updateJob(@PathVariable("tid") String tid)
    {
        etlTaskEntryService.updateJob(Arrays.asList(tid.split(",")));
        return Map.of("success", true, "message", "success");
    }

    @PostMapping("/execute/{tid}")
    public Map<String, Object> executeTask(@PathVariable("tid") String tid)
    {
        EtlTask etlTask = new EtlTask(tid, "manual", Map.of("tid", tid));
        boolean success = queueManager.executeEtlTaskLogic(etlTask);
        return Map.of("success", success, "message", success ? "任务已执行" : "任务执行失败");
    }
}
