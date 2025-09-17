package com.wgzhao.addax.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpAloneService {

    @Value("${sp.alone.webhook.url:https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=e8594ef9-6a35-494b-b2f6-55c4bad60ec0}")
    private String webhookUrl;

    @Value("${sp.alone.root.dir:/opt/infalog}")
    private String rootDir;

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /** 执行Redis命令 */
    public String executeRedisCommand(String command) {
        if (command == null || command.isBlank()) return null;
        try {
            String[] parts = command.trim().split("\\s+", 3);
            String op = parts[0].toLowerCase(Locale.ROOT);
            switch (op) {
                case "get": {
                    String key = parts.length > 1 ? parts[1] : null;
                    return key == null ? null : stringRedisTemplate.opsForValue().get(key);
                }
                case "set": {
                    // 允许 value 含空格，使用第三段
                    if (parts.length < 3) return null;
                    String key = parts[1];
                    String value = parts[2];
                    stringRedisTemplate.opsForValue().set(key, value);
                    // 模拟 redis-cli 返回 OK
                    return "OK";
                }
                case "del": {
                    if (parts.length < 2) return "0";
                    Boolean deleted = stringRedisTemplate.delete(parts[1]);
                    return Boolean.TRUE.equals(deleted) ? "1" : "0";
                }
                case "keys": {
                    if (parts.length < 2) return "";
                    Set<String> keys = stringRedisTemplate.keys(parts[1]);
                    return (keys == null || keys.isEmpty()) ? "" : String.join("\n", keys);
                }
                case "sadd": {
                    // sadd 1 member
                    if (parts.length < 3) return "0";
                    String setKey = parts[1];
                    String member = parts[2];
                    Long added = stringRedisTemplate.opsForSet().add(setKey, member);
                    return String.valueOf(added == null ? 0 : added);
                }
                case "rem": {
                    if (parts.length < 3) return "0";
                    String setKey = parts[1];
                    String member = parts[2];
                    Long rem = stringRedisTemplate.opsForSet().remove(setKey, member);
                    return String.valueOf(rem == null ? 0 : rem);
                }
                case "sismember": {
                    if (parts.length < 3) return "0";
                    String setKey = parts[1];
                    String member = parts[2];
                    Boolean hit = stringRedisTemplate.opsForSet().isMember(setKey, member);
                    return Boolean.TRUE.equals(hit) ? "1" : "0";
                }
                case "smembers": {
                    if (parts.length < 2) return "";
                    String setKey = parts[1];
                    Set<String> members = stringRedisTemplate.opsForSet().members(setKey);
                    if (members == null || members.isEmpty()) return "";
                    return String.join("\n", members);
                }
                default:
                    log.warn("未支持的Redis命令: {}", command);
                    return null;
            }
        } catch (Exception e) {
            log.error("执行Redis命令失败: {}", command, e);
            return null;
        }
    }

    /** 执行Redis标志操作 */
    public String executeRedisFlag(String action, String flag) {
        if (!"all".equalsIgnoreCase(action) && (flag == null || flag.isBlank())) return null;
        String setKey = "1"; // 与脚本保持一致
        try {
            switch (action) {
                case "add": {
                    Long added = stringRedisTemplate.opsForSet().add(setKey, flag);
                    return String.valueOf(added == null ? 0 : added);
                }
                case "has": {
                    Boolean hit = stringRedisTemplate.opsForSet().isMember(setKey, flag);
                    return Boolean.TRUE.equals(hit) ? "1" : "0";
                }
                case "all": {
                    Set<String> members = stringRedisTemplate.opsForSet().members(setKey);
                    if (members == null || members.isEmpty()) return "";
                    return String.join("\n", members);
                }
                case "rem": {
                    Long rem = stringRedisTemplate.opsForSet().remove(setKey, flag);
                    return String.valueOf(rem == null ? 0 : rem);
                }
                case "wtin": {
                    while (!Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(setKey, flag))) {
                        log.info("标志[{}]不存在，等待标志出现...", flag);
                        sleep(1000);
                    }
                    return "wait completed";
                }
                case "wtout": {
                    while (Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(setKey, flag))) {
                        log.info("标志[{}]存在，等待标志消失...", flag);
                        sleep(1000);
                    }
                    return "wait completed";
                }
                default:
                    log.warn("未支持的rfg action: {}", action);
                    return null;
            }
        } catch (Exception e) {
            log.error("rfg 执行失败: action={}, flag={}", action, flag, e);
            return null;
        }
    }

    /** 等待队列索引 */
    public Integer waitForIndex(String name, Integer maxCount) {
        if (maxCount == null || maxCount <= 0) {
            return 0;
        }
        while (true) {
            for (int idx = 1; idx <= maxCount; idx++) {
                String flagName = name + "_" + idx;
                if ("1".equals(executeRedisFlag("add", flagName))) {
                    return idx;
                }
            }
            sleep(1000);
        }
    }

    /** 执行SQL文件 */
    public Integer runSqlFile(String sqlFile, Integer parallelNum) {
        try {
            Path sqlPath = Paths.get(sqlFile);
            if (!Files.exists(sqlPath)) return 1;
            List<String> lines = Files.readAllLines(sqlPath);
            if (lines.size() < 4) return 1;
            String dbUser = lines.get(0);
            String dbPass = lines.get(1);
            String dbUrl = lines.get(2);
            String parallelName = "runsql_" + UUID.randomUUID().toString().replace("-", "");
            executeRedisCommand("set " + parallelName + " 0");
            CountDownLatch latch = new CountDownLatch(lines.size() - 3);
            for (int i = 3; i < lines.size(); i++) {
                String sql = lines.get(i);
                if (sql == null || sql.isBlank()) { latch.countDown(); continue; }
                executorService.submit(() -> {
                    try {
                        int idx = waitForIndex(parallelName, parallelNum == null ? 1 : parallelNum);
                        String taskName = parallelName + "_" + idx;
                        int result = executeJdbcCommand(dbUrl, dbUser, dbPass, sql);
                        if (result != 0) executeRedisCommand("set " + parallelName + " " + result);
                        executeRedisFlag("rem", taskName);
                    } finally { latch.countDown(); }
                });
            }
            latch.await();
            String result = executeRedisCommand("get " + parallelName);
            executeRedisCommand("del " + parallelName);
            return result != null ? Integer.parseInt(result) : 0;
        } catch (Exception e) {
            log.error("执行SQL文件失败: {}", sqlFile, e);
            return 1;
        }
    }

    /** 在数据库源库执行语句 */
    public String executeSourceDbSql(String sysId, String sql) {
        try {
            String dbConn = querySingleCell("select db_conn from vw_imp_system where sysid='" + sysId + "'");
            if (dbConn == null) return "无法获取系统 " + sysId + " 的数据库连接信息";
            return executeJdbcCommandWithResult(dbConn, sql);
        } catch (Exception e) {
            log.error("执行源库SQL失败: sysId={}, sql={}", sysId, sql, e);
            return "执行失败: " + e.getMessage();
        }
    }

    /** 记录运行日志 */
    public void recordSystemLog(String content) {
        try {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new Date());
            String hostname = getHostname();
            long pid = ProcessHandle.current().pid();
            String logDir = rootDir + "/log";
            Files.createDirectories(Paths.get(logDir));
            String logFile = logDir + "/tuna_syslog_" + today + "_00.log";
            String logEntry = String.format("%s|%s|%d|%s%n", timestamp, hostname, pid, content);
            Files.write(Paths.get(logFile), logEntry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("记录系统日志失败", e);
        }
    }

    /** 提示信息 */
    public String generateTip(String scriptName, String status, String message) {
        String shName = executeRedisCommand("get shname." + scriptName);
        String shStatus = executeRedisCommand("get com.shsts_" + status);
        if (shName == null || shName.isEmpty()) shName = "未知名称:" + scriptName;
        if (shStatus == null || shStatus.isEmpty()) shStatus = "未知状态:" + status;
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String hostname = getHostname();
        return String.format("--------[<b>%s</b>]***%s***[当前时间:%s][服务器:%s][%s]--------\n",
            shName, shStatus, currentTime, hostname, message != null ? message : "");
    }


    // 辅助
    private String getHostname() { try { return java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "unknown"; } }

    private int executeJdbcCommand(String dbUrl, String dbUser, String dbPass, String sql) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(dbUrl, dbUser, dbPass);
             java.sql.Statement st = conn.createStatement()) {
            boolean hasRs = st.execute(sql);
            if (hasRs) try (java.sql.ResultSet rs = st.getResultSet()) { while (rs.next()) { /* consume */ } }
            return 0;
        } catch (Exception e) { log.error("JDBC执行失败", e); return 1; }
    }

    private String executeJdbcCommandWithResult(String dbUrl, String sql) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(dbUrl);
             java.sql.Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) { Object v = rs.getObject(1); return v == null ? null : String.valueOf(v); }
            return null;
        } catch (Exception e) { log.error("JDBC查询失败", e); return null; }
    }

    private String querySingleCell(String sql) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows.isEmpty()) return null;
            Object v = rows.get(0).values().stream().findFirst().orElse(null);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            try { jdbcTemplate.execute(sql); } catch (Exception ignore) {}
            return null;
        }
    }

    private List<String> querySingleColumn(String sql) {
        try { return jdbcTemplate.query(sql, (rs, n) -> rs.getString(1)); }
        catch (Exception e) { log.error("查询失败: {}", sql, e); return Collections.emptyList(); }
    }

    private void executeSqlStatement(String sql) { jdbcTemplate.execute(sql); }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

}
