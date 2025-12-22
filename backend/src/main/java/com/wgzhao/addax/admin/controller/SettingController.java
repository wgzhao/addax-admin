package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TargetService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 系统配置接口
 */
@RestController
@RequestMapping("/settings")
@AllArgsConstructor
@Slf4j
public class SettingController
{

    private final DictService dictService;
    private final TargetService targetService;
    private final SystemConfigService systemConfigService;

    // 获取 dictCode 为 1000 的系统配置项
    @GetMapping("/sys-config")
    public ResponseEntity<Map<String, Object>> getSysConfig()
    {
        return ResponseEntity.ok().body(dictService.getSysConfig());
    }

    @PostMapping("/sys-config")
    public ResponseEntity<Void> updateSysConfig(@RequestBody Map<String, Object> payload)
    {
        dictService.saveSysConfig(payload);
        try {
            // Refresh centralized config in Redis so other nodes see changes
            systemConfigService.reloadFromDictAndBroadcast();
        }
        catch (Exception e) {
            log.warn("Failed to reload system config into Redis after update: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reload-sys-config")
    public ResponseEntity<String> reloadSysConfig()
    {
        systemConfigService.loadConfig();
        return ResponseEntity.ok("System configuration reloaded successfully");
    }

    @PostMapping("/test-hive-connect")
    public ResponseEntity<String> testHiveConnect(@RequestBody HiveConnectDto hiveConnectDto)
    {
        try {
            DataSource hiveDataSourceWithConfig = targetService.getHiveDataSourceWithConfig(hiveConnectDto);
            Connection connection = hiveDataSourceWithConfig.getConnection();
            log.info("Successfully connected to Hive with config: {}, {}", hiveConnectDto, connection);
            return ResponseEntity.noContent().build();
        }
        catch (SQLException | MalformedURLException e) {
            log.error("Failed to connect to Hive with config: {}", hiveConnectDto, e);
            throw new ApiException(400, "Failed to connect to Hive: " + e.getMessage());
        }
    }

    @PostMapping("/addax-hdfs-writer-template")
    public ResponseEntity<String> updateAddaxHdfsWriterTemplate(@RequestBody @NonNull String template)
    {
        dictService.saveHdfsWriteTemplate(template);
        return ResponseEntity.ok().body("HDFS Writer template updated successfully");
    }

    // 获取采集模板，这里主要是三个模板
    // 采集主模板，关系性数据库读取模板，HDFS写入模板
    @GetMapping("/job-templates")
    public ResponseEntity<Map<String, SysItem>> getJobTemplates()
    {
        Map<String, SysItem> templates = dictService.getJobTemplates();
        return ResponseEntity.ok().body(templates);
    }

    @PutMapping("/job-templates")
    public ResponseEntity<String> updateJobTemplates(@RequestBody List<SysItem> templates)
    {
        dictService.updateJobTemplates(templates);
        return ResponseEntity.ok().body("Job templates updated successfully");
    }
}
