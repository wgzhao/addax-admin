package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.HiveConnectDto;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
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
        String bizDateStr = dictService.getBizDate();
        tmp.put("BIZ_DATE", bizDateStr);
        try {
            LocalDate bizDate =  LocalDate.ofInstant(new SimpleDateFormat("yyyyMMdd").parse(bizDateStr).toInstant(), java.time.ZoneId.systemDefault());
            tmp.put("BIZ_DATE_AS_DATE", bizDate);
        } catch (ParseException e) {
            log.error("Failed to parse BIZ_DATE: {}", bizDateStr, e);
            tmp.put("BIZ_DATE_AS_DATE", null);
        }

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

        // schema refresh timeout (seconds) - optional config item in sys_item dict code 1000
        Integer schemaTimeout = dictService.getItemValue(1000, "SCHEMA_REFRESH_TIMEOUT", Integer.class);
        tmp.put("SCHEMA_REFRESH_TIMEOUT", schemaTimeout == null ? 600 : schemaTimeout);

        // Atomically replace the contents of the ConcurrentHashMap to allow safe reloads
        configCache.clear();
        configCache.putAll(tmp);
    }

    public String getBizDate()
    {
        return (String) configCache.get("BIZ_DATE");
    }

    public LocalDate getBizDateAsDate()
    {
        return (LocalDate) configCache.getOrDefault("BIZ_DATE_AS_DATE", LocalDate.now().plusDays(-1));
    }

    public String getSwitchTime()
    {
        return (String) configCache.get("SWITCH_TIME");
    }

    public LocalTime getSwitchTimeAsTime() {
        String switchTime = getSwitchTime();
        return LocalTime.parse(switchTime, DateTimeFormatter.ofPattern("HH:mm"));
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

    /**
     * 获取 schema 刷新超时时间（秒），默认为 600 秒
     * @return 超时时间（秒）
     */
    public int getSchemaRefreshTimeoutSeconds() {
        Object v = configCache.get("SCHEMA_REFRESH_TIMEOUT");
        if (v instanceof Integer) return (Integer) v;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 600; }
    }

}
