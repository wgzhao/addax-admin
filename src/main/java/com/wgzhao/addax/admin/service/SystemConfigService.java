package com.wgzhao.addax.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemConfigService
{
    @Autowired
    private DictService dictService;

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

}
