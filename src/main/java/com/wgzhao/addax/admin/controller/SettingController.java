package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.TargetService;
import org.springframework.beans.factory.annotation.Autowired;
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
public class SettingController {

    @Autowired
    private DictService dictService;

    @Autowired
    private TargetService targetService;

    // 获取 dictCode 为 1000 的系统配置项
    @GetMapping("/sys-config")
    public ResponseEntity<Map<String, String>> getSysConfig() {

        return ResponseEntity.ok().body(dictService.getSysConfig());
    }

    @PostMapping("/test-hive-connect")
    public ResponseEntity<String> testHiveConnect(HiveConnectDto hiveConnectDto) {
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
