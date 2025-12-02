package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.service.SystemFlagService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class SystemFlagController {
    private final SystemFlagService systemFlagService;

    @GetMapping("/api/system/refresh/status")
    public RefreshStatus status() {
        boolean inProgress = systemFlagService.isRefreshInProgress();
        return new RefreshStatus(inProgress, inProgress ? "刷新进行中" : "空闲");
    }

    @Data
    public static class RefreshStatus {
        private final boolean refreshing;
        private final String message;
    }
}
