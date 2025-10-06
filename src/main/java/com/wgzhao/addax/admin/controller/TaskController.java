package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.EtlJourService;
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

import java.util.List;
import java.util.Map;

/**
 * 采集任务管理接口（RESTful规范），提供采集任务及队列相关操作
 */
@Tag(name = "采集任务管理", description = "采集任务及队列相关接口")
@RestController
@RequestMapping("/tasks")
@Slf4j
@AllArgsConstructor
public class TaskController
{
    /** 任务服务 */
    private final TaskService taskService;
    /** 队列管理器 */
    private final TaskQueueManager queueManager;
    /** 表服务 */
    private final TableService tableService;
    /** 作业内容服务 */
    private final JobContentService jobContentService;
    /** 日志服务 */
    private final EtlJourService jourService;

    /**
     * 获取队列状态
     * @return 当前采集任务队列状态
     */
    @Operation(summary = "获取队列状态", description = "获取当前采集任务队列的状态")
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueStatus()
    {
        return ResponseEntity.ok(taskService.getEtlQueueStatus());
    }

    /**
     * 更改队列监控器状态（启动或停止）
     * @param payload 请求体，需包含 state 字段，值为 'running' 或 'stopped'
     * @return 操作结果
     */
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
            result = taskService.startQueueMonitor();
        }
        else {
            throw new ApiException(400, "Invalid state value");
        }
        return ResponseEntity.ok(Map.of("result", result));
    }

    /**
     * 重置队列
     * @return 操作结果
     */
    @Operation(summary = "重置队列", description = "重置采集任务队列，清空所有等待中的任务")
    @PostMapping("/queue/actions/reset")
    public ResponseEntity<Map<String, Object>> resetQueue()
    {
        String result = taskService.resetQueue();
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    /**
     * 立即更新所有任务
     * @return 操作结果
     */
    @Operation(summary = "立即更新所有任务", description = "立即更新所有有效的采集任务的配置")
    @PostMapping("/addax-jobs")
    public ResponseEntity<Map<String, Object>> updateAllJobs()
    {
        for (VwEtlTableWithSource table : tableService.getValidTableViews()) {
            jobContentService.updateJob(table);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    /**
     * 立即更新单任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    @Operation(summary = "立即更新单个任务", description = "根据任务ID立即更新单个采集任务的配置")
    @PutMapping("/{taskId}/addax-job")
    public ResponseEntity<Map<String, Object>> updateJob(
            @Parameter(description = "任务ID") @PathVariable("taskId") long taskId)
    {
        jobContentService.updateJob(tableService.getTableView(taskId));
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    /**
     * 执行采集任务
     * @param taskId 任务ID
     * @param isSync 是否同步执行，默认 false
     * @return 任务执行结果
     */
    @Operation(summary = "执行采集任务", description = "根据任务ID立即执行单个采集任务")
    @PostMapping("/{taskId}/executions")
    public ResponseEntity<TaskResultDto> executeTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") long taskId,
            @Parameter(description = "是否同步执行，默认 false") @RequestParam(name = "isSync", required = false, defaultValue = "false") boolean isSync)
    {
        TaskResultDto result;
        if (isSync) {
            EtlTable etlTable = tableService.getTable(taskId);
            if (etlTable == null) {
                throw new ApiException(400, "taskId 对应的采集任务不存在");
            }
            result = queueManager.executeEtlTaskWithConcurrencyControl(etlTable);
        } else {
            result = taskService.submitTask(taskId);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 批量异步执行采集任务
     * @param tids 任务ID列表
     * @return 执行结果摘要
     */
    @Operation(summary = "批量异步执行采集任务", description = "根据任务ID列表异步执行多个采集任务")
    @PostMapping("/executions/batch")
    public ResponseEntity<TaskResultDto> executeTasksBatch(
            @RequestBody(description = "请求体，需包含 taskIds 字段，值为任务ID列表")
            @org.springframework.web.bind.annotation.RequestBody List<Long> tids)
    {
        int successCount = 0;
        int failCount = 0;
        TaskResultDto result;
        for (Long tid : tids) {
            result = taskService.submitTask(tid);
            if (result.isSuccess()) {
                successCount++;
            }
            else {
                failCount++;
            }
        }
        return ResponseEntity.ok(TaskResultDto.success(
                String.format("批量任务提交完成: 成功 %d 个，失败 %d 个", successCount, failCount), 0));
    }

    /**
     * 采集任务状态查询
     * @return 所有采集任务的最新状态列表
     */
    @Operation(summary = "采集任务状态查询", description = "查询采集任务的最新状态")
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getAllTaskStatus()
    {
        List<Map<String, Object>> status = taskService.getAllTaskStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 获取指定采集表的最后错误信息
     * @param tableId 采集表ID
     * @return 错误信息
     */
    @Operation(summary = "获取指定采集表的最后错误信息", description = "根据采集表ID获取该表最近一次采集任务的错误信息")
    @GetMapping("/{tableId}/last-error")
    public ResponseEntity<String> getLastErrorByTableId(
            @Parameter(description = "采集表ID") @PathVariable("tableId") long tableId)
    {
        String errorMsg = jourService.findLastErrorByTableId(tableId);
        if (errorMsg == null || errorMsg.isEmpty()) {
            return ResponseEntity.ok("No error message found for the given table ID");
        }
        else {
            return ResponseEntity.ok(errorMsg);
        }
    }
}
