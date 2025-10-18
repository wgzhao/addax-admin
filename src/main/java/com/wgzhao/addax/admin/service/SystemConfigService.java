package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@AllArgsConstructor
@Slf4j
public class SystemConfigService
{
    private final DictService dictService;

    private final Map<String, Object> configCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initConfig() {
        // Load configuration at bean initialization so dependent beans can read config in their @PostConstruct
        loadConfig();
        log.info("SystemConfigService: configuration initialized");
    }

    public void loadConfig()
    {
        // Build a temporary map first to reduce the window where partial updates are visible
        Map<String, Object> tmp = new HashMap<>();

        String curDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        tmp.put("BIZ_DATE", dictService.getBizDate());
        tmp.put("CUR_DATETIME", curDateTime);

        tmp.put("LOG_PATH", dictService.getLogPath());

        // 切日时间
        tmp.put("SWITCH_TIME", dictService.getSwitchTime());
        // hive

        tmp.put("HIVE_CLI", dictService.getHiveCli());

        tmp.put("HDFS_PREFIX", dictService.getHdfsPrefix());

        tmp.put("HIVE_SERVER2", dictService.getHiveServer2());

        tmp.put("CONCURRENT_LIMIT", dictService.getConcurrentLimit());
        tmp.put("QUEUE_SIZE", dictService.getQueueSize());
        tmp.put("ADDAX_HOME", dictService.getAddaxHome());

        // Atomically replace the contents of the ConcurrentHashMap to allow safe reloads
        configCache.clear();
        configCache.putAll(tmp);
    }

    public String getBizDate()
    {
        return (String) configCache.get("BIZ_DATE");
    }

    public String getLogPath()
    {
        return (String) configCache.get("LOG_PATH");
    }

    public String getCurDateTime()
    {
        return (String) configCache.get("CUR_DATETIME");
    }

    public String getSwitchTime()
    {
        return (String) configCache.get("SWITCH_TIME");
    }

    public LocalTime getSwitchTimeAsTime() {
        String switchTime = getSwitchTime();
        return LocalTime.parse(switchTime, DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String getHDFSPrefix()
    {
        return (String) configCache.get("HDFS_PREFIX");

    }

    public HiveConnectDto getHiveServer2() {
        return (HiveConnectDto) configCache.get("HIVE_SERVER2");
    }

    public int getConcurrentLimit()
    {
        return (Integer) configCache.get("CONCURRENT_LIMIT");
    }
    public int getQueueSize()
    {
        return (Integer) configCache.get("QUEUE_SIZE");
    }

    public String getAddaxHome()
    {
        return (String) configCache.get("ADDAX_HOME");
    }

}
