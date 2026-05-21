package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
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

    @PostMapping("/refresh/start")
    public ResponseEntity<String> startRefresh()
    {
        taskService.updateParams();
        return ResponseEntity.ok("开始切日更新");
    }

    public record RefreshStatus(boolean refreshing, String message)
        {
        }
}
