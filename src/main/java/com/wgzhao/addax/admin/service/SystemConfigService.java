package com.wgzhao.addax.admin.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemConfigService
{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Map<String, Object> configCache = new HashMap<>();

    public void loadConfig()
    {
        String curDate =  LocalDate.now().format(sdf);
        String curDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String sql ="select entry_value from tb_dictionary where entry_code = 1021 and entry_value < '" + curDate + "' order by entry_value desc limit 1";
        // 业务日期，格式为 yyyyMMdd, 一般为上一个工作日
        String bizDate = jdbcTemplate.queryForObject(sql, String.class);
        if (bizDate == null || bizDate.isEmpty()) {
            bizDate = curDate;
        }
        configCache.put("BIZ_DATE", bizDate);
        configCache.put("CUR_DATETIME", curDateTime);
        // 日志路径
        sql = "select entry_content from tb_dictionary where entry_code = 1062 and entry_value = 'runlog'";
        String logPath = jdbcTemplate.queryForObject(sql, String.class);
        if (logPath == null || logPath.isEmpty()) {
            // 当前程序运行目录的 logs 目录
            logPath = System.getProperty("user.dir") + "/logs";
        }
        configCache.put("LOG_PATH", logPath);

        // hive
    }

    public String getBizDate() {
        return (String) configCache.get("BIZ_DATE");
    }

    public String getLogPath() {
        return (String) configCache.get("LOG_PATH");
    }

    public String getCurDateTime() {
        return (String) configCache.get("CUR_DATETIME");
    }
}
