package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TaskService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 采集任务管理接口（RESTful 规范）
 */
@Tag(name = "采集任务管理", description = "采集任务及队列相关接口")
@RestController
@RequestMapping("/tasks")
@Slf4j
@AllArgsConstructor
public class TaskController
{
    private final TaskService taskService;
    private final TaskQueueManager queueManager;
    private final TableService tableService;
    private final JobContentService jobContentService;

    // 获取队列状态
    @Operation(summary = "获取队列状态", description = "获取当前采集任务队列的状态")
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueStatus()
    {
        return ResponseEntity.ok(taskService.getEtlQueueStatus());
    }

    // 更改队列监控器状态
    @Operation(summary = "更改队列监控器状态", description = "启动或停止队列监控器")
    @PatchMapping("/queue")
    public ResponseEntity<Map<String, Object>> configureQueue(
            @RequestBody(description = "请求体，需包含 state 字段，值为 'running' 或 'stopped'")
            @org.springframework.web.bind.annotation.RequestBody Map<String, String> payload)
    {
        String state = payload.get("state");
        String result;
        if ("stopped".equalsIgnoreCase(state)) {
            result = taskService.stopQueueMonitor();
        }
        else if ("running".equalsIgnoreCase(state)) {
            result = taskService.restartQueueMonitor();
        }
        else {
            throw new ApiException(400, "Invalid state. Allowed values are 'running' or 'stopped'.");
        }
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    // 重置队列
    @Operation(summary = "重置队列", description = "重置采集任务队列，清空所有等待中的任务")
    @PostMapping("/queue/actions/reset")
    public ResponseEntity<Map<String, Object>> resetQueue()
    {
        String result = taskService.resetQueue();
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    // 立即更新所有任务
    @Operation(summary = "立即更新所有任务", description = "立即更新所有有效的采集任务的配置")
    @PostMapping("/jobs")
    public ResponseEntity<Map<String, Object>> updateAllJobs()
    {
        for (VwEtlTableWithSource table : tableService.getValidTableViews()) {
            jobContentService.updateJob(table);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    // 立即更新单任务
    @Operation(summary = "立即更新单个任务", description = "根据任务ID立即更新单个采集任务的配置")
    @PutMapping("/{taskId}/job")
    public ResponseEntity<Map<String, Object>> updateJob(
            @Parameter(description = "任务ID") @PathVariable("taskId") long taskId)
    {
        jobContentService.updateJob(tableService.getTableView(taskId));
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    // 执行采集任务
    @Operation(summary = "执行采集任务", description = "根据任务ID立即执行单个采集任务")
    @PostMapping("/{taskId}/executions")
    public ResponseEntity<Map<String, Object>> executeTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") long taskId)
    {
        EtlTable etlTable = tableService.getTable(taskId);
        if (etlTable == null) {
            throw new ApiException(400, "taskId 对应的采集任务不存在");
        }
        boolean isSuccess = queueManager.executeEtlTaskWithConcurrencyControl(etlTable);
        if (!isSuccess) {
            log.warn("任务执行失败，taskId: {}", taskId);
            throw new ApiException(500, "任务执行失败，可能是并发数已达上限");
        }
        else {
            return ResponseEntity.ok(Map.of("taskId", taskId, "message", "任务已执行"));
        }
    }
}
