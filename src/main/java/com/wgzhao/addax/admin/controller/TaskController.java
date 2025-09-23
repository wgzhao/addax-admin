package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.TableService;
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

import java.util.Map;

/**
 * 采集任务管理接口
 */
@RestController
@RequestMapping("/etl")
@CrossOrigin
@Slf4j
public class TaskController
{
    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskQueueManager queueManager;

    @Autowired private TableService tableService;
    @Autowired private JobContentService jobContentService;

    /**
     * 启动采集任务入口 - 扫描数据库并加入队列
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟的第0秒执行
    @PostMapping("/start")
    public void startEtlTasks()
    {
        log.info("接收到启动采集任务的请求");
        taskService.executePlanStartWithQueue();
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

    // 立即更新任务
    // 可以传递一个包含 tid 数组的 JSON 对象，类似
    // { "tid": [1, 2, 3] }
    // 如果不传递任何参数，则更新所有任务
    @PostMapping("/updateJob")
    public Map<String, Object> updateJob()
    {
        for (EtlTable table : tableService.getValidTables()) {
            jobContentService.updateJob(table);
        };
        return Map.of("success", true, "message", "success");
    }

    @PostMapping("/updateJob/{tid}")
    public Map<String, Object> updateJob(@PathVariable("tid") long tid)
    {
        jobContentService.updateJob(tableService.getTable(tid));
        return Map.of("success", true, "message", "success");
    }

    @PostMapping("/execute/{tid}")
    public ApiResponse<Map<String, Object>> executeTask(@PathVariable("tid") long tid)
    {
        EtlTable etlTable = tableService.getTable(tid);
        if (etlTable == null) {
            return ApiResponse.error(400, "tid 对应的采集任务不存在");
        }
        boolean isSuccess = queueManager.executeEtlTaskWithConcurrencyControl(etlTable);
        if (!isSuccess) {
            log.warn("任务执行失败，tid: {}", tid);
            return ApiResponse.error(500, "任务执行失败，可能是并发数已达上限");
        } else {
            return ApiResponse.success(Map.of("tid", tid, "message", "任务已执行"));
        }
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
