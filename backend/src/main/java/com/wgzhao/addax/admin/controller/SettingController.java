package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.scheduler.SchemaRefreshScheduler;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TargetService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final SchemaRefreshScheduler schemaRefreshScheduler;
    private final AuditRecorder auditRecorder;

    // 获取 dictCode 为 1000 的系统配置项
    @GetMapping("/sys-config")
    public ResponseEntity<Map<String, Object>> getSysConfig()
    {
        return ResponseEntity.ok().body(dictService.getSysConfig());
    }

    @PostMapping("/sys-config")
    @Transactional
    public ResponseEntity<Void> updateSysConfig(@RequestBody Map<String, Object> payload)
    {
        Map<String, Object> before = dictService.getSysConfig();
        dictService.saveSysConfig(payload);
        try {
            // Refresh centralized config in Redis so other nodes see changes
            systemConfigService.reloadFromDictAndBroadcast();
        }
        catch (Exception e) {
            log.warn("Failed to reload system config into Redis after update: {}", e.getMessage());
        }
        auditRecorder.success("SYSTEM_CONFIG_UPDATED", "CONFIG", "CRITICAL", "SYSTEM_CONFIG",
            "1000", "system config", before, payload);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reload-sys-config")
    @Transactional
    public ResponseEntity<String> reloadSysConfig()
    {
        systemConfigService.loadConfig();
        auditRecorder.success("SYSTEM_CONFIG_RELOADED", "CONFIG", "WARNING", "SYSTEM_CONFIG",
            "1000", "system config", null, null);
        return ResponseEntity.ok("System configuration reloaded successfully");
    }

    @PostMapping("/test-hive-connect")
    @Transactional
    public ResponseEntity<String> testHiveConnect(@RequestBody HiveConnectDto hiveConnectDto)
    {
        try {
            DataSource hiveDataSourceWithConfig = targetService.getHiveDataSourceWithConfig(hiveConnectDto);
            try (Connection connection = hiveDataSourceWithConfig.getConnection()) {
                log.info("Successfully connected to Hive with url: {}", hiveConnectDto.url());
            }
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("url", hiveConnectDto.url());
            metadata.put("username", hiveConnectDto.username());
            auditRecorder.success("HIVE_CONNECTION_TESTED", "CONFIG", "INFO", "SYSTEM_CONFIG",
                "HIVE_SERVER2", "HiveServer2", null, null,
                metadata);
            return ResponseEntity.noContent().build();
        }
        catch (SQLException | MalformedURLException e) {
            log.error("Failed to connect to Hive with url: {}", hiveConnectDto.url(), e);
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("url", hiveConnectDto.url());
            metadata.put("username", hiveConnectDto.username());
            auditRecorder.independentFailure("HIVE_CONNECTION_TESTED", "CONFIG", "WARNING",
                "SYSTEM_CONFIG", "HIVE_SERVER2", "HiveServer2", null, "HIVE_CONNECTION_FAILED", e,
                metadata);
            throw new ApiException(400, "Failed to connect to Hive: " + e.getMessage());
        }
    }

    @PostMapping("/addax-hdfs-writer-template")
    @Transactional
    public ResponseEntity<String> updateAddaxHdfsWriterTemplate(@RequestBody @NonNull String template)
    {
        String before = dictService.getHdfsWriterTemplate();
        dictService.saveHdfsWriteTemplate(template);
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("beforeDigest", digest(before));
        metadata.put("afterDigest", digest(template));
        auditRecorder.success("HDFS_WRITER_TEMPLATE_UPDATED", "CONFIG", "CRITICAL", "JOB_TEMPLATE",
            "HDFS_CONFIG", "HDFS writer template", null, null,
            metadata);
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
    @Transactional
    public ResponseEntity<String> updateJobTemplates(@RequestBody List<SysItem> templates)
    {
        Map<String, SysItem> before = dictService.getJobTemplates();
        dictService.updateJobTemplates(templates);
        auditRecorder.success("JOB_TEMPLATES_UPDATED", "CONFIG", "CRITICAL", "JOB_TEMPLATE",
            "job-templates", "job templates", before, templates);
        return ResponseEntity.ok().body("Job templates updated successfully");
    }

    /**
     * 触发切日(SWITCH_TIME)相关定时任务重新注册。
     *
     * 前端在修改 SWITCH_TIME 后调用此接口，使 SchemaRefreshScheduler 立刻按新时间重建 cron trigger。
     */
    @PostMapping("/reschedule-switch-time-task")
    @Transactional
    public ResponseEntity<String> rescheduleSwitchTimeTask()
    {
        schemaRefreshScheduler.reschedule();
        auditRecorder.success("SWITCH_TIME_TASK_RESCHEDULED", "CONFIG", "WARNING", "SYSTEM_CONFIG",
            "SWITCH_TIME", "switch-time scheduler", null, null);
        return ResponseEntity.ok("Schema refresh task rescheduled");
    }

    private String digest(String value)
    {
        if (value == null) {
            return null;
        }
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        }
        catch (Exception ex) {
            return null;
        }
    }
}
