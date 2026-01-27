package com.wgzhao.addax.admin.service;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static com.wgzhao.addax.admin.common.Constants.shortSdf;

@Service
@AllArgsConstructor
@Slf4j
public class SystemConfigService
{
    // No local cache: read templates/config on demand from Redis keys with prefix
    private static final String REDIS_CONFIG_PREFIX = "system:config:"; // full key = prefix + CONFIG_KEY
    private static final String REDIS_CONFIG_CHANNEL = "system:config:reload";
    private final DictService dictService;
    private final StringRedisTemplate redisTemplate;
    private final Environment env;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void initConfig()
    {
        // Ensure Redis has configuration; if not, bootstrap from dict
        try {
            Boolean exists = redisTemplate.hasKey(REDIS_CONFIG_PREFIX + "RDBMS_READER_TEMPLATE");
            if (!exists) {
                reloadFromDictAndBroadcast();
            }
        }
        catch (Exception e) {
            log.warn("Failed to read redis config key, falling back to bootstrap: {}", e.getMessage());
            reloadFromDictAndBroadcast();
        }
        log.info("SystemConfigService: configuration initialized");
    }

    // Build config from dict, write to redis hash and publish reload event
    public synchronized void reloadFromDictAndBroadcast()
    {
        Map<String, Object> tmp = buildConfigFromDict();
        try {
            // Convert all values to JSON/string and store each as a separate Redis key under prefix
            for (Map.Entry<String, Object> e : tmp.entrySet()) {
                Object val = e.getValue();
                if (val == null) {
                    continue;
                }
                String s;
                if (val instanceof String) {
                    s = (String) val;
                }
                else {
                    try {
                        s = objectMapper.writeValueAsString(val);
                    }
                    catch (Exception ex) {
                        s = val.toString();
                    }
                }
                try {
                    redisTemplate.opsForValue().set(REDIS_CONFIG_PREFIX + e.getKey(), s);
                }
                catch (Exception ex) {
                    log.warn("Failed to set redis key for {}: {}", e.getKey(), ex.getMessage());
                }
            }
            // publish a simple reload notification
            redisTemplate.convertAndSend(REDIS_CONFIG_CHANNEL, String.valueOf(System.currentTimeMillis()));
        }
        catch (Exception e) {
            log.error("Failed to write config to redis hash: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildConfigFromDict()
    {
        Map<String, Object> tmp = new HashMap<>();
        String bizDateStr = dictService.getBizDate();
        tmp.put("BIZ_DATE", bizDateStr);
        LocalDate bizDate = DateUtil.parse(bizDateStr, shortSdf).toLocalDateTime().toLocalDate();
        tmp.put("BIZ_DATE_AS_DATE", bizDate);
        tmp.put("CUR_DATETIME", DateUtil.now());
        tmp.put("LOG_PATH", dictService.getLogPath());
        tmp.put("SWITCH_TIME", dictService.getSwitchTime());
        tmp.put("HIVE_CLI", dictService.getHiveCli());
        tmp.put("HDFS_PREFIX", dictService.getHdfsPrefix());
        tmp.put("HIVE_SERVER2", dictService.getHiveServer2());
        tmp.put("CONCURRENT_LIMIT", dictService.getConcurrentLimit());
        tmp.put("QUEUE_SIZE", dictService.getQueueSize());
        tmp.put("ADDAX_HOME", dictService.getAddaxHome());
        tmp.put("SCHEMA_REFRESH_TIMEOUT", dictService.getSchemaRefreshTimeout());
        tmp.put("RDBMS_READER_TEMPLATE", dictService.getRdbmsReaderTemplate());
        tmp.put("HDFS_WRITER_TEMPLATE", dictService.getHdfsWriterTemplate());
        tmp.put("RDBMS2HDFS_JOB_TEMPLATE", dictService.getRdbms2HdfsJobTemplate());
        tmp.put("L2TD", dictService.bizDateAdd(bizDateStr, -1));
        return tmp;
    }

    public String getBizDate()
    {
        try {
            return redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "BIZ_DATE");
        }
        catch (Exception e) {
            log.warn("Failed to read BIZ_DATE from redis: {}", e.getMessage());
            return dictService.getBizDate();
        }
    }

    public LocalDate getBizDateAsDate()
    {
        try {
            String s =  redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "BIZ_DATE_AS_DATE");
            if (s != null) {
                return objectMapper.readValue(s, LocalDate.class);
            }
        }
        catch (Exception e) {
           log.error("Failed to parse BIZ_DATE_AS_DATE from redis: {}", e.getMessage());
        }
        return  DateUtil.parse(dictService.getBizDate(), shortSdf).toLocalDateTime().toLocalDate();
    }

    public String getSwitchTime()
    {
        try {
            return redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "SWITCH_TIME");
        }
        catch (Exception e) {
            log.warn("Failed to read SWITCH_TIME from redis: {}", e.getMessage());
            return dictService.getSwitchTime();
        }
    }

    public LocalTime getSwitchTimeAsTime()
    {
        String switchTime = getSwitchTime();
        return LocalTime.parse(switchTime, DateTimeFormatter.ofPattern("HH:mm"));
    }

    public HiveConnectDto getHiveServer2()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "HIVE_SERVER2");
            if (s != null) {
                return objectMapper.readValue(s, HiveConnectDto.class);
            }
        }
        catch (Exception e) {
            log.error("Failed to parse HIVE_SERVER2 config: {}", e.getMessage());
        }
        return dictService.getHiveServer2();
    }

    public int getConcurrentLimit()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "CONCURRENT_LIMIT");
            if (s != null) {
                return Integer.parseInt(s);
            }
        }
        catch (Exception e) {
            log.warn("Failed to read CONCURRENT_LIMIT from redis: {}", e.getMessage());
        }
        return dictService.getConcurrentLimit();
    }

    public int getQueueSize()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "QUEUE_SIZE");
            if (s != null) {
                return Integer.parseInt(s);
            }
        }
        catch (Exception e) {
            log.warn("Failed to read QUEUE_SIZE from redis: {}", e.getMessage());
        }
        return dictService.getQueueSize();
    }

    /**
     * Node-level concurrency weight factor in range [0.0, 1.0].
     * Default is 1.0 when not configured.
     */
    public double getNodeConcurrencyWeight()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "NODE_CONCURRENCY_WEIGHT");
            if (s != null) {
                double v = Double.parseDouble(s);
                if (Double.isFinite(v)) {
                    if (v < 0.0) return 0.2;
                    return Math.min(v, 1.0);
                }
            }
        }
        catch (Exception e) {
            log.warn("Failed to read NODE_CONCURRENCY_WEIGHT from redis: {}", e.getMessage());
        }
        // try application.properties / environment variable
        try {
            String s2 = env.getProperty("node.concurrency.weight");
            if (s2 != null) {
                double v2 = Double.parseDouble(s2);
                if (Double.isFinite(v2)) {
                    if (v2 < 0.0) return 0.0;
                    return Math.min(v2, 1.0);
                }
            }
        }
        catch (Exception e) {
            // ignore
        }
        // fallback default
        return 1.0;
    }

    /**
     * 获取 schema 刷新超时时间（秒），默认为 600 秒
     *
     * @return 超时时间（秒）
     */
    public int getSchemaRefreshTimeoutSeconds()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "SCHEMA_REFRESH_TIMEOUT");
            if (s != null) {
                return Integer.parseInt(s);
            }
        }
        catch (Exception e) {
            log.warn("Failed to read SCHEMA_REFRESH_TIMEOUT from redis: {}", e.getMessage());
        }
        return 600;
    }

    public String getHdfsPrefix()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "HDFS_PREFIX");
            if (s != null) {
                return s;
            }
        }
        catch (Exception e) {
            log.warn("Failed to read HDFS_PREFIX from redis: {}", e.getMessage());
        }
        return dictService.getHdfsPrefix();
    }

    public String getRdbmsReaderTemplate()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "RDBMS_READER_TEMPLATE");
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        catch (Exception e) {
            log.warn("Failed to read RDBMS_READER_TEMPLATE from redis: {}", e.getMessage());
        }
        return dictService.getRdbmsReaderTemplate();
    }

    public String getHdfsWriterTemplate()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "HDFS_WRITER_TEMPLATE");
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        catch (Exception e) {
            log.warn("Failed to read HDFS_WRITER_TEMPLATE from redis: {}", e.getMessage());
        }
        return dictService.getHdfsWriterTemplate();
    }

    public String getRdbms2HdfsJobTemplate()
    {
        try {
            String s = redisTemplate.opsForValue().get(REDIS_CONFIG_PREFIX + "RDBMS2HDFS_JOB_TEMPLATE");
            if (s != null && !s.isEmpty()) {
                return s;
            }
        }
        catch (Exception e) {
            log.warn("Failed to read RDBMS2HDFS_JOB_TEMPLATE from redis: {}", e.getMessage());
        }
        return dictService.getRdbms2HdfsJobTemplate();
    }

    /**
     * Backwards-compatible alias used by existing callers. This will reload from dict and broadcast to other instances.
     */
    public void loadConfig()
    {
        reloadFromDictAndBroadcast();
    }

    public Map<String, String> getBizDateValues()
    {

        Map<String, String> values = new HashMap<>();
        LocalDate bizDate = getBizDateAsDate();
        values.put("biz_date_short", getBizDate());
        values.put("biz_date_dash", bizDate.format(DateTimeFormatter.ISO_LOCAL_DATE));

        values.put("biz_year", String.valueOf(bizDate.getYear()));
        values.put("biz_month", String.format("%02d", bizDate.getMonthValue()));
        values.put("biz_short_month", String.format("%d", bizDate.getMonthValue()));
        values.put("biz_day", String.format("%02d", bizDate.getDayOfMonth()));
        values.put("biz_short_day", String.format("%d", bizDate.getDayOfMonth()));
        values.put("biz_ym", bizDate.format(DateTimeFormatter.ofPattern("yyyyMM")));
        values.put("biz_short_ym", bizDate.format(DateTimeFormatter.ofPattern("yyyyM")));

        LocalDateTime dt = bizDate.atTime(LocalTime.now());
        values.put("biz_datetime_short", dt.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        values.put("biz_datetime_dash", dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        values.put("biz_datetime_0_dash", dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00")));
        values.put("biz_datetime_0_short", dt.format(DateTimeFormatter.ofPattern("yyyyMMdd000000")));

        // current date time
        LocalDateTime now = LocalDateTime.now();
        values.put("curr_date_short", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        values.put("curr_date_dash", now.format(DateTimeFormatter.ISO_LOCAL_DATE));
        values.put("curr_datetime_short", now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        values.put("curr_datetime_dash", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        values.put("curr_datetime_0_dash", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd 00:00:00")));
        values.put("curr_datetime_0_short", now.format(DateTimeFormatter.ofPattern("yyyyMMdd000000")));
        return values;
    }
}
