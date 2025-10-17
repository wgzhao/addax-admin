package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TargetService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.Map;

/**
 * 系统配置接口
 */
@RestController
@RequestMapping("/settings")
@AllArgsConstructor
public class SettingController
{

    private final DictService dictService;
    private final TargetService targetService;
    private final SystemConfigService systemConfigService;

    // 获取 dictCode 为 1000 的系统配置项
    @GetMapping("/sys-config")
    public ResponseEntity<Map<String, String>> getSysConfig()
    {
        return ResponseEntity.ok().body(dictService.getSysConfig());
    }

    @PostMapping("/sys-config")
    public ResponseEntity<SysItem> updateSysConfig(@RequestBody SysItem sysItem)
    {
        if (sysItem.getDictCode() != 1000) {
            return ResponseEntity.badRequest().build();
        }
        dictService.updateSysConfig(sysItem);
        return ResponseEntity.ok().body(sysItem);
    }

    @PostMapping("/reload-sys-config")
    public ResponseEntity<String> reloadSysConfig() {
        systemConfigService.loadConfig();
        return ResponseEntity.ok("System configuration reloaded successfully");
    }

    @PostMapping("/test-hive-connect")
    public ResponseEntity<String> testHiveConnect(HiveConnectDto hiveConnectDto)
    {
        DataSource hiveDataSourceWithConfig = targetService.getHiveDataSourceWithConfig(hiveConnectDto);
        try {
            if (hiveDataSourceWithConfig != null && hiveDataSourceWithConfig.getConnection() != null) {
                return ResponseEntity.ok("Hive connection successful");
            }
            else {
                return ResponseEntity.status(500).body("Failed to connect to Hive");
            }
        }
        catch (SQLException e) {
            return ResponseEntity.status(500).body("Failed to connect to Hive: " + e.getMessage());
        }
    }
}
