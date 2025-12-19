package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.service.TaskService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/system")
public class SystemFlagController {
    private final RedisLockService redisLockService;
    private final TaskService taskService;

    @GetMapping("/refresh/status")
    public RefreshStatus status() {
        boolean inProgress = redisLockService.isRefreshInProgress();
        return new RefreshStatus(inProgress, inProgress ? "刷新进行中" : "空闲");
    }

    @PostMapping("/refresh/start")
    public ResponseEntity<String> startRefresh() {
        taskService.updateParams();
        return ResponseEntity.ok("开始切日更新");
    }

    @Data
    public static class RefreshStatus {
        private final boolean refreshing;
        private final String message;
    }
}
