package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.exception.ApiException
import com.wgzhao.addax.admin.service.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 采集任务管理接口（RESTful规范），提供采集任务及队列相关操作
 */
@Tag(name = "采集任务管理", description = "采集任务及队列相关接口")
@RestController
@RequestMapping("/tasks")
class TaskController(
    private val taskService: TaskService,
    private val queueManager: TaskQueueManager,
    private val tableService: TableService,
    private val jobContentService: JobContentService,
    private val jourService: EtlJourService
) {

    @GetMapping("/queue")
    @Operation(summary = "获取队列状态", description = "获取当前采集任务队列的状态")
    fun queueStatus(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(taskService.etlQueueStatus)

    @PatchMapping("/queue")
    @Operation(summary = "更改队列监控器状态", description = "启动或停止队列监控器")
    fun configureQueue(@RequestBody payload: Map<String, String>): ResponseEntity<Map<String, Any>> {
        val state = payload["state"]
        val result = when (state?.lowercase()) {
            "stopped" -> taskService.stopQueueMonitor()
            "running" -> taskService.startQueueMonitor()
            else -> throw ApiException(400, "Invalid state value")
        }
        return ResponseEntity.ok(mapOf("result" to result))
    }

    @PostMapping("/queue/actions/reset")
    @Operation(summary = "重置队列", description = "重置采集任务队列，清空所有等待中的任务")
    fun resetQueue(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "message" to taskService.resetQueue()))

    @PostMapping("/addax-jobs")
    @Operation(summary = "立即更新所有任务", description = "立即更新所有有效的采集任务的配置")
    fun updateAllJobs(): ResponseEntity<Map<String, Any>> {
        tableService.validTableViews?.forEach { jobContentService.updateJob(it) }
        return ResponseEntity.ok(mapOf("success" to true, "message" to "success"))
    }

    /**
     * 立即更新单任务
     * @param taskId 任务ID
     * @return 操作结果
     */
    @Operation(summary = "立即更新单个任务", description = "根据任务ID立即更新单个采集任务的配置")
    @PutMapping("/{taskId}/addax-job")
    fun updateJob(
        @PathVariable("taskId") taskId: Long
    ): ResponseEntity<Map<String, Any>> {
        jobContentService.updateJob(tableService.getTableView(taskId))
        return ResponseEntity.ok(mapOf("success" to true, "message" to "success"))
    }

    /**
     * 执行采集任务
     * @param taskId 任务ID
     * @param isSync 是否同步执行，默认 false
     * @return 任务执行结果
     */
    @Operation(summary = "执行采集任务", description = "根据任务ID立即执行单个采集任务")
    @PostMapping("/{taskId}/executions")
    suspend fun executeTask(
        @PathVariable("taskId") taskId: Long,
        @RequestParam(name = "isSync", required = false, defaultValue = "false") isSync: Boolean
    ): ResponseEntity<TaskResultDto?> {
        val result: TaskResultDto?
        if (isSync) {
            val etlTable = tableService.getTable(taskId) ?: throw ApiException(400, "taskId 对应的采集任务不存在")
            result = queueManager.executeEtlTaskWithConcurrencyControl(etlTable)
        } else {
            result = taskService.submitTask(taskId)
        }
        return ResponseEntity.ok(result)
    }

    /**
     * 批量异步执行采集任务
     * @param tids 任务ID列表
     * @return 执行结果摘要
     */
    @Operation(summary = "批量异步执行采集任务", description = "根据任务ID列表异步执行多个采集任务")
    @PostMapping("/executions/batch")
    fun executeTasksBatch(
        @RequestBody tids: MutableList<Long>
    ): ResponseEntity<TaskResultDto?> {
        var successCount = 0
        var failCount = 0
        var result: TaskResultDto?
        for (tid in tids) {
            result = taskService.submitTask(tid)
            if (result.success) {
                successCount++
            } else {
                failCount++
            }
        }
        return ResponseEntity.ok<TaskResultDto?>(
            TaskResultDto.success(
                String.format("批量任务提交完成: 成功 %d 个，失败 %d 个", successCount, failCount), 0
            )
        )
    }

    /**
     * 采集任务状态查询
     * @return 所有采集任务的最新状态列表
     */
    @GetMapping("/status")
    @Operation(summary = "采集任务状态查询", description = "查询采集任务的最新状态")
    fun allTaskStatus(): ResponseEntity<List<Map<String, Any>?>?>
    {
        val status = taskService.getAllTaskStatus()
        return ResponseEntity.ok(status)

    }

    /**
     * 获取指定采集表的最后错误信息
     * @param tableId 采集表ID
     * @return 错误信息
     */
    @GetMapping("/{tableId}/last-error")
    @Operation(summary = "获取指定采集表的最后错误信息", description = "根据采集表ID获取该表最近一次采集任务的错误信息")
    fun getLastErrorByTableId(
        @Parameter(description = "采集表ID") @PathVariable("tableId") tableId: Long
    ): ResponseEntity<String?> {
        val errorMsg = jourService.findLastErrorByTableId(tableId)
        if (errorMsg == null || errorMsg.isEmpty()) {
            return ResponseEntity.ok<String?>("No error message found for the given table ID")
        } else {
            return ResponseEntity.ok<String?>(errorMsg)
        }
    }
}
