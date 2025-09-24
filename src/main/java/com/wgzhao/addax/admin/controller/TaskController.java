package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TaskService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 采集任务管理接口（RESTful 规范）
 */
@RestController
@RequestMapping("/tasks")
@CrossOrigin
@Slf4j
public class TaskController {
    @Autowired
    private TaskService taskService;
    @Autowired
    private TaskQueueManager queueManager;
    @Autowired private TableService tableService;
    @Autowired private JobContentService jobContentService;

    // 启动采集任务计划（计划任务入口）
    @Scheduled(cron = "0 * * * * ?")
    @PostMapping("/start")
    public ResponseEntity<Void> startEtlTasks() {
        taskService.executePlanStartWithQueue();
        return ResponseEntity.accepted().build();
    }

    // 获取队列状态
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        return ResponseEntity.ok(taskService.getEtlQueueStatus());
    }

    // 停止队列监控
    @PostMapping("/queue/stop")
    public ResponseEntity<Map<String, Object>> stopQueueMonitor() {
        String result = taskService.stopQueueMonitor();
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    // 重启队列监控
    @PostMapping("/queue/restart")
    public ResponseEntity<Map<String, Object>> restartQueueMonitor() {
        String result = taskService.restartQueueMonitor();
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    // 重置队列
    @PostMapping("/queue/reset")
    public ResponseEntity<Map<String, Object>> resetQueue() {
        String result = taskService.resetQueue();
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    // 立即更新所有任务
    @PostMapping("/update-job")
    public ResponseEntity<Map<String, Object>> updateJob() {
        for (EtlTable table : tableService.getValidTables()) {
            jobContentService.updateJob(table);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    // 立即更新单任务
    @PostMapping("/{taskId}/update-job")
    public ResponseEntity<Map<String, Object>> updateJob(@PathVariable("taskId") long taskId) {
        jobContentService.updateJob(tableService.getTable(taskId));
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    // 执行采集任务
    @PostMapping("/{taskId}/execute")
    public ResponseEntity<Map<String, Object>> executeTask(@PathVariable("taskId") long taskId) {
        EtlTable etlTable = tableService.getTable(taskId);
        if (etlTable == null) {
            throw new ApiException(400, "taskId 对应的采集任务不存在");
        }
        boolean isSuccess = queueManager.executeEtlTaskWithConcurrencyControl(etlTable);
        if (!isSuccess) {
            log.warn("任务执行失败，taskId: {}", taskId);
            throw new ApiException(500, "任务执行失败，可能是并发数已达上限");
        } else {
            return ResponseEntity.ok(Map.of("taskId", taskId, "message", "任务已执行"));
        }
    }
}
