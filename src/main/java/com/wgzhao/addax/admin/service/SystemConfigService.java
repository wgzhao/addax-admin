package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@AllArgsConstructor
public class SystemConfigService
{
    private final DictService dictService;

    private final Map<String, Object> configCache = new HashMap<>();

    public void loadConfig()
    {
        String curDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        configCache.put("BIZ_DATE", dictService.getBizDate());
        configCache.put("CUR_DATETIME", curDateTime);

        configCache.put("LOG_PATH", dictService.getLogPath());

        // 切日时间
        configCache.put("SWITCH_TIME", dictService.getSwitchTime());
        // hive

        configCache.put("HIVE_CLI", dictService.getHiveCli());

        configCache.put("HDFS_PREFIX", dictService.getHdfsPrefix());

        configCache.put("HIVE_SERVER2", dictService.getHiveServer2());

        configCache.put("CONCURRENT_LIMIT", dictService.getConcurrentLimit());
        configCache.put("QUEUE_SIZE", dictService.getQueueSize());
        configCache.put("ADDAX_HOME", dictService.getAddaxHome());
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
