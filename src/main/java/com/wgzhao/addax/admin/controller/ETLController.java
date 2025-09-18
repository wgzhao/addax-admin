package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.EtlTask;
import com.wgzhao.addax.admin.model.TbAddaxStatistic;
import com.wgzhao.addax.admin.model.TbImpEtl;
import com.wgzhao.addax.admin.repository.TbImpEtlRepo;
import com.wgzhao.addax.admin.service.AddaxStatService;
import com.wgzhao.addax.admin.service.TaskService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
    private TaskService taskService;

    @Autowired
    private TaskQueueManager queueManager;

    @Autowired
    private TbImpEtlRepo impEtlRepo;
    @Autowired private AddaxStatService addaxStatService;

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

    /**
     * 启动采集任务入口 - 扫描数据库并加入队列
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟的第0秒执行
    @PostMapping("/start")
    public Map<String, Object> startEtlTasks()
    {
        log.info("接收到启动采集任务的请求");
        String result = taskService.executePlanStartWithQueue();

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
        return taskService.getEtlQueueStatus();
    }

    /**
     * 手动添加任务到队列
     */
//    @PostMapping("/add/{tid}")
//    public Map<String, Object> addTaskToQueue(@PathVariable("tid") String tid)
//    {
//        boolean success = queueManager.addTaskToQueue(tid);
//
//        return Map.of(
//                "success", success,
//                "message", success ? "任务已加入队列" : "添加任务失败",
//                "tid", tid
//        );
//    }

    /**
     * 停止队列监控
     */
    @PostMapping("/stop")
    public Map<String, Object> stopQueueMonitor()
    {
        String result = taskService.stopQueueMonitor();
        return Map.of("success", true, "message", result);
    }

    /**
     * 重启队列监控
     */
    @PostMapping("/restart")
    public Map<String, Object> restartQueueMonitor()
    {
        String result = taskService.restartQueueMonitor();
        return Map.of("success", true, "message", result);
    }

    /**
     * 重置队列
     */
    @PostMapping("/reset")
    public Map<String, Object> resetQueue()
    {
        String result = taskService.resetQueue();
        return Map.of("success", true, "message", result);
    }

    @PostMapping("/updateJob")
    public Map<String, Object> updateJob()
    {
        taskService.updateJob(null);
        return Map.of("success", true, "message", "success");
    }

    @PostMapping("/updateJob/{tid}")
    public Map<String, Object> updateJob(@PathVariable("tid") String tid)
    {
        taskService.updateJob(Arrays.asList(tid.split(",")));
        return Map.of("success", true, "message", "success");
    }

    @PostMapping("/execute/{tid}")
    public Map<String, Object> executeTask(@PathVariable("tid") String tid)
    {
        TbImpEtl tbImpEtl = impEtlRepo.findById(tid).orElseThrow();
        Map<String, Object> etlData = Map.of("dest_db", "ods" + tbImpEtl.getSouSysid().toLowerCase(), "dest_tablename", tbImpEtl.getDestTablename());
        EtlTask etlTask = new EtlTask(tid, "manual",etlData);
        boolean isSuccess = queueManager.executeEtlTaskWithConcurrencyControl(etlTask);
        return Map.of("success", isSuccess, "message",isSuccess ? "任务已执行" : "任务执行失败");
    }

    /**
     * 更新参数接口
     */
//    @PostMapping("/updt-param")
//    public ResponseEntity<String> updtParam() {
//        String result = taskService.updateParameters();
//        return ResponseEntity.ok(result);
//    }

}
