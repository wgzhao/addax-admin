package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.DayCutOutcome;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/system")
public class SystemFlagController
{
    private final TaskQueueManager queueManager;
    private final TaskService taskService;

    @GetMapping("/refresh/status")
    public RefreshStatus status()
    {
        boolean inProgress = queueManager.isRefreshing();
        return new RefreshStatus(inProgress, inProgress ? "刷新进行中" : "空闲");
    }

    /**
     * Manual day-cut trigger. The response reports what actually happened: the old
     * constant "开始切日更新" was returned even on a worker node or while another
     * refresh held the lock, so the caller had no way to tell it did nothing.
     */
    @PostMapping("/refresh/start")
    public ResponseEntity<String> startRefresh()
    {
        DayCutOutcome outcome = taskService.updateParams();
        if (outcome.skipped()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("未执行切日刷新：" + outcome.skipReason());
        }
        if (!outcome.success()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("切日刷新失败（阶段：" + outcome.stage() + "）：" + outcome.error());
        }
        String suffix = outcome.refresh().skippedSourceDown() > 0
            ? String.format("、源不可达跳过 %d 张", outcome.refresh().skippedSourceDown()) : "";
        return ResponseEntity.ok(String.format(
            "切日刷新完成：重置 %d 张表，刷新成功 %d 张、失败 %d 张%s，耗时 %d 秒。",
            outcome.resetTables(), outcome.refresh().success(), outcome.refresh().failed(), suffix,
            outcome.durationMs() / 1000));
    }

    public record RefreshStatus(boolean refreshing, String message)
        {
        }
}
