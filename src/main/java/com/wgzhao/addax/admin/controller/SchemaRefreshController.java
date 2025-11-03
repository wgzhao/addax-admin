package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.service.SystemFlagService;
import com.wgzhao.addax.admin.service.TableService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/refresh")
@AllArgsConstructor
public class SchemaRefreshController {
    private final SystemFlagService systemFlagService;
    private final TableService tableService;

    @PostMapping("/schema")
    public ApiResponse<String> triggerRefresh() {
        boolean acquired = systemFlagService.beginRefresh("manual");
        if (!acquired) {
            return ApiResponse.error(409, "Schema refresh already in progress");
        }
        try {
            tableService.refreshAllTableResources();
            return ApiResponse.success("schema refresh started and finished");
        }
        catch (Exception e) {
            return ApiResponse.error(500, "schema refresh failed: " + e.getMessage());
        }
        finally {
            systemFlagService.endRefresh("manual");
        }
    }

    @GetMapping("/status")
    public ApiResponse<Boolean> status() {
        return ApiResponse.success(systemFlagService.isRefreshInProgress());
    }
}

