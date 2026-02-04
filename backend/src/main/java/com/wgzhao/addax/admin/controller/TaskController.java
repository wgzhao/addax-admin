package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 采集任务管理接口（RESTful规范），提供采集任务及队列相关操作
 */
@RestController
@RequestMapping("/tasks")
@Slf4j
@AllArgsConstructor
public class TaskController
{
    /**
     * 任务服务
     */
    private final TaskService taskService;
    /**
     * 队列管理器
     */
    private final TaskQueueManager queueManager;
    /**
     * 表服务
     */
    private final TableService tableService;
    /**
     * 作业内容服务
     */
    private final JobContentService jobContentService;
    /**
     * 日志服务
     */
    private final EtlJourService jourService;

    /**
     * 获取队列状态
     *
     * @return 当前采集任务队列状态
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueStatus()
    {
        return ResponseEntity.ok(taskService.getEtlQueueStatus());
    }

    /**
     * 更改队列监控器状态（启动或停止）
     * 请求体: { "state": "running" | "stopped" }
     *
     * @param payload 请求体，需包含 state 字段，值为 'running' 或 'stopped'
     * @return 操作结果
     */
    @PatchMapping("/queue")
    public ResponseEntity<Map<String, Object>> configureQueue(
        @RequestBody Map<String, String> payload)
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
     *
     * @return 操作结果
     */
    @PostMapping("/queue/actions/reset")
    public ResponseEntity<Map<String, Object>> resetQueue()
    {
        String result = taskService.resetQueue();
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    /**
     * 立即更新所有任务
     *
     * @return 操作结果
     */
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
     *
     * @param taskId 任务ID（路径参数）
     * @return 操作结果
     */
    @PutMapping("/{taskId}/addax-job")
    public ResponseEntity<Map<String, Object>> updateJob(@PathVariable long taskId)
    {
        jobContentService.updateJob(tableService.getTableView(taskId));
        return ResponseEntity.ok(Map.of("success", true, "message", "success"));
    }

    /**
     * 执行采集任务
     *
     * @param taskId 任务ID（路径参数）
     * @param isSync 是否同步执行，默认 false（查询参数）
     * @return 任务执行结果
     */
    @PostMapping("/{taskId}/executions")
    public ResponseEntity<TaskResultDto> executeTask(
        @PathVariable long taskId,
        @RequestParam(name = "isSync", required = false, defaultValue = "false") boolean isSync)
    {
        TaskResultDto result;
        if (isSync) {
            EtlTable etlTable = tableService.getTable(taskId);
            if (etlTable == null) {
                throw new ApiException(400, "taskId 对应的采集任务不存在");
            }
            result = queueManager.executeEtlTaskWithConcurrencyControl(etlTable);
        }
        else {
            result = taskService.submitTask(taskId, getCurrentUsername());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 批量异步执行采集任务
     * 请求体: List<Long> tids
     *
     * @param tids 任务ID列表（请求体）
     * @return 执行结果摘要
     */
    @PostMapping("/executions/batch")
    public ResponseEntity<TaskResultDto> executeTasksBatch(
        @RequestBody List<Long> tids)
    {
        int successCount = 0;
        int failCount = 0;
        TaskResultDto result;
        String username = getCurrentUsername();
        for (Long tid : tids) {
            result = taskService.submitTask(tid, username);
            if (result.success()) {
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
     *
     * @return 所有采集任务的最新状态列表
     */
    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getAllTaskStatus()
    {
        List<Map<String, Object>> status = taskService.getAllTaskStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 获取指定采集表的最后错误信息
     *
     * @param tableId 采集表ID（路径参数）
     * @return 错误信息或提示
     */
    @GetMapping("/{tableId}/last-error")
    public ResponseEntity<String> getLastErrorByTableId(
        @PathVariable long tableId)
    {
        String errorMsg = jourService.findLastErrorByTableId(tableId);
        if (errorMsg == null || errorMsg.isEmpty()) {
            return ResponseEntity.ok("No error message found for the given table ID");
        }
        else {
            return ResponseEntity.ok(errorMsg);
        }
    }

    /**
     * 终止正在运行的任务
     *
     * @param tid 表 ID（路径参数）
     * @return 任务终止结果
     */
    @PostMapping("/{tid}/kill")
    public ResponseEntity<TaskResultDto> killTask(
        @PathVariable long tid)
    {
        TaskResultDto result = taskService.killTask(tid);
        return ResponseEntity.ok(result);
    }

    private String getCurrentUsername()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getName();
    }
}
