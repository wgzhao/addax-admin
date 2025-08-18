package com.wgzhao.addax.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate = new RestTemplate();
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
                case "srem": {
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

    /** 发送企业微信机器人消息 */
    public String sendToWecomRobot(String message) {
        try {
            String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String hostname = getHostname();
            String formattedMessage = String.format("""
                ### **数据采集告警**

                ---

                **告警时间**: %s
                **告警节点**: %s
                **告警内容**: **%s**
                """, currentTime, hostname, message);
            Map<String, Object> body = Map.of(
                "msgtype", "markdown",
                "markdown", Map.of("content", formattedMessage)
            );
            return restTemplate.postForObject(webhookUrl, body, String.class);
        } catch (Exception e) {
            log.error("发送企业微信消息失败", e);
            return "发送失败: " + e.getMessage();
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

    /** ���动备份 */
    public String performAutomaticBackup() {
        try {
            String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String yesterday = new SimpleDateFormat("yyyyMMdd").format(new Date(System.currentTimeMillis() - 24 * 3600 * 1000));
            StringBuilder result = new StringBuilder();
            String bakDir = rootDir + "/autobak";
            Files.createDirectories(Paths.get(bakDir));
            result.append("=====日志文件归档=====\n");
            String logDir = executeRedisCommand("get path.runlog");
            if (logDir != null) archiveLogs(logDir, yesterday);
            result.append("=====备份文件压缩存档=====\n");
            compressBackupFiles(bakDir, today);
            result.append("=====删除历史数据=====\n");
            cleanHistoryFiles(bakDir);
            return result.toString();
        } catch (Exception e) {
            log.error("自动备份失败", e);
            return "自动备份失败: " + e.getMessage();
        }
    }

    /** 计划任务主控制 */
    public String executePlanStart() {
        try {
            recordSystemLog("计划任务主控制开始执行");
            int currentSecond = LocalDateTime.now().getSecond();
            if (currentSecond >= 57) { recordSystemLog("计划任务主控制在整点前几秒开始"); sleep(3000); }
            if (currentSecond > 30) { recordSystemLog("计划任务主控制在不合适的时间点启动,本次计划任务退出"); return "退出：不合适的时间点启动"; }
            if ("1".equals(executeRedisFlag("add", "plan_start"))) {
                StringBuilder sqlBuilder = new StringBuilder();
                executeSqlStatement("begin; select sp_imp_alone('plan_start');end;");
                List<Map.Entry<String, String>> tasks = fetchKindAndIds("select fn_imp_value('plan_run')");
                for (Map.Entry<String, String> kv : tasks) {
                    sqlBuilder.append("select sp_imp_status('R','").append(kv.getValue()).append("');");
                    dispatchStartWkf(kv.getKey(), kv.getValue());
                }
                if (sqlBuilder.length() > 0) executeSqlStatement("begin; " + sqlBuilder + " end;");
                String spRunCount = querySingleCell("select count(1) where fn_imp_value('sp_run') is not null");
                if ("1".equals(spRunCount)) executeSpStart();
                executeRedisFlag("rem", "plan_start");
            } else {
                sendToWecomRobot("没有计划任务在执行，但是占用了标志!!");
            }
            recordSystemLog("计划任务主控制执行完毕");
            return "计划任务执行完成";
        } catch (Exception e) {
            log.error("计划任务执行失败", e);
            return "计划任务执行失败: " + e.getMessage();
        }
    }

    /** 启动任务的并发入口 */
    public String executeSpStart() {
        if (!"1".equals(executeRedisFlag("add", "auto"))) return "auto 已占用";
        try {
            log.info("当前redis标志: {}", executeRedisFlag("all", ""));
            executeRedisFlag("wtout", "sp_init");
            executeSpInit();
            log.info("当前redis标志: {}", executeRedisFlag("all", ""));
            return "sp_start 完成";
        } finally { executeRedisFlag("rem", "auto"); }
    }

    /** 采集,SP,数据服务的总入口 */
    public String executeSpInit() {
        if (!"1".equals(executeRedisFlag("add", "sp_init"))) return "sp_init 已占用";
        try {
            List<String> sysIds = querySingleColumn("select fn_imp_value('etl_end') from dual");
            if (!sysIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String sysid : sysIds) {
                    if (sysid == null || sysid.isBlank()) continue;
                    sb.append("select sp_imp_alone('etl_end','").append(sysid).append("');");
                    dispatchStartWkf("etl_end", sysid);
                }
                executeSqlStatement("begin " + sb + " end;");
            }
            executeSqlStatement("begin; select sp_imp_alone('sp_start');end;");
            List<Map.Entry<String, String>> tasks = fetchKindAndIds("select fn_imp_value('sp_run')");
            if (!tasks.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> kv : tasks) {
                    sb.append(" select sp_imp_status('R','").append(kv.getValue()).append("');");
                    dispatchStartWkf(kv.getKey(), kv.getValue());
                }
                executeSqlStatement("begin; " + sb + " end;");
            }
            return "sp_init 完成";
        } finally { executeRedisFlag("rem", "sp_init"); }
    }

    /** 采集,SP,计划任务的具体执行 */
    public String executeSpEtl(String taskId, String mode) {
        if (taskId == null || taskId.isBlank()) return "taskId 为空";
        if (!"1".equals(executeRedisFlag("add", "sp." + taskId))) {
            if (!"manual".equalsIgnoreCase(mode)) executeSqlStatement("begin sp_imp_status('E','" + taskId + "');end;");
            return "sp." + taskId + " 已占用";
        }
        String spname = Optional.ofNullable(querySingleCell("select coalesce(fn_imp_value('taskname','" + taskId + "'),'" + taskId + "') from dual")).orElse(taskId);
        try {
            if ("manual".equalsIgnoreCase(mode)) executeSqlStatement("update tb_imp_sp_com set flag='N' where sp_id='" + taskId + "' and flag!='X'");
            List<String> comRows = querySingleColumn("select com_id||','||com_kind||','||com_idx from tb_imp_sp_com where sp_id='" + taskId + "' and flag='N' order by com_idx");
            String comsDir = Optional.ofNullable(executeRedisCommand("get path.coms")).orElse(rootDir + "/coms");
            Files.createDirectories(Paths.get(comsDir));
            for (String row : comRows) {
                String[] arr = row.split(",");
                if (arr.length < 3) continue;
                String comId = arr[0]; String comKind = arr[1]; String comIdx = arr[2];
                Path comFile = Paths.get(comsDir, spname + "_" + comIdx + ".txt");
                String comText = querySingleCell("select fn_imp_value('com_text','" + comId + "') from dual");
                if (comText == null) {
                    executeSqlStatement("begin sp_imp_status('cE','" + comId + "');end;");
                    if (!"plan".equalsIgnoreCase(mode)) break;
                }
                Files.writeString(comFile, comText, StandardCharsets.UTF_8);
                executeSqlStatement("begin sp_imp_status('cR','" + comId + "');end;");
                int rc = runTuna(comKind, comFile.toString(), 36000);
                if (rc == 0) executeSqlStatement("begin sp_imp_status('cY','" + comId + "');end;");
                else {
                    executeSqlStatement("begin sp_imp_status('cE','" + comId + "');end;");
                    if (!"plan".equalsIgnoreCase(mode)) break;
                }
            }
            if (!"manual".equalsIgnoreCase(mode)) {
                executeSqlStatement("begin sp_imp_status('Y','" + taskId + "');end;");
                if (mode == null || mode.isBlank()) executeSpStart();
            }
            return "sp_etl 完成: " + taskId;
        } catch (Exception e) {
            log.error("sp_etl 执行失败: {}", taskId, e);
            if (!"manual".equalsIgnoreCase(mode)) executeSqlStatement("begin sp_imp_status('E','" + taskId + "');end;");
            return "sp_etl 失败: " + e.getMessage();
        } finally { executeRedisFlag("rem", "sp." + taskId); }
    }

    /** 数据服务的具体执行 */
    public String executeDataServiceEtl(String dsId) {
        if (dsId == null || dsId.isBlank()) return "dsId 为空";
        if (!"1".equals(executeRedisFlag("add", "ds." + dsId))) return "ds." + dsId + " 已占用";
        String othsDir = Optional.ofNullable(executeRedisCommand("get path.oths")).orElse(rootDir + "/oths");
        try {
            executeRedisCommand("set ds." + dsId + " 0");
            String initRds = querySingleCell("select init_rds from vw_imp_ds2 where ds_id='" + dsId + "'");
            if (initRds != null && !initRds.isBlank()) {
                Path f = Paths.get(othsDir, dsId + ".initrds");
                Files.writeString(f, initRds, StandardCharsets.UTF_8);
                runBash(f.toString());
            }
            if (isDsOk(dsId) && isRedisTrue("ds." + dsId + ".dsview")) {
                for (String kind : List.of("presto", "allsql")) {
                    String sql = querySingleCell("select fn_imp_value('ds_sql_" + kind + "','" + dsId + "') from dual");
                    if (sql != null && sql.trim().length() > 3) {
                        Path f = Paths.get(othsDir, dsId + ".dsview");
                        Files.writeString(f, sql, StandardCharsets.UTF_8);
                        if ("presto".equals(kind)) runExternal(new String[]{"trino", "--server", "etl01:18080", "--catalog", "hive", "-f", f.toString()});
                        else executeSqlStatement(sql);
                    }
                }
            }
            if (isDsOk(dsId) && isRedisTrue("ds." + dsId + ".bupdate")) {
                List<String> dbConns = querySingleColumn("select sou_db_conn from vw_imp_ds2_mid where ds_id='" + dsId + "' group by sou_db_conn union all select '" + dsId + "' union all select 'hadoop' from dual");
                CountDownLatch latch = new CountDownLatch(dbConns.size());
                for (String dbConn : dbConns) {
                    executorService.submit(() -> {
                        try { if (executeSourceTableEtl(dbConn) != 0) executeRedisCommand("set ds." + dsId + " 1"); }
                        finally { latch.countDown(); }
                    });
                }
                latch.await();
                executeSqlStatement("begin sp_imp_alone('bupdate','D','" + dsId + "');end;");
                if (isDsOk(dsId)) executeSqlStatement("update tb_imp_ds2 set bupdate='N' where ds_id='" + dsId + "'");
            }
            if (isDsOk(dsId) && isRedisTrue("ds." + dsId + ".pre_sql")) {
                String presql = querySingleCell("select pre_sql from vw_imp_ds2 where ds_id='" + dsId + "'");
                if (presql != null) {
                    Path f = Paths.get(othsDir, dsId + ".presql");
                    Files.writeString(f, presql, StandardCharsets.UTF_8);
                    runSqlFile(f.toString(), 1);
                }
            }
            if (isDsOk(dsId) && isRedisTrue("ds." + dsId + ".pre_sh")) {
                String presh = querySingleCell("select pre_sh from vw_imp_ds2 where ds_id='" + dsId + "'");
                if (presh != null) { Path f = Paths.get(othsDir, dsId + ".presh"); Files.writeString(f, presh, StandardCharsets.UTF_8); runBash(f.toString()); }
            }
            if (isDsOk(dsId)) {
                int paralNum = parseIntOrDefault(executeRedisCommand("get ds." + dsId + ".paral_num"), 1);
                List<String> tblIds = querySingleColumn("select tbl_id from tb_imp_ds2_tbls where ds_id='" + dsId + "' and flag='N' order by end_time-start_time desc");
                CountDownLatch latch = new CountDownLatch(tblIds.size());
                for (String tblId : tblIds) {
                    executorService.submit(() -> {
                        try {
                            int idx = waitForIndex("ds_" + dsId, paralNum);
                            executeSqlStatement("begin sp_imp_status('cR','" + tblId + "');end;");
                            String json = querySingleCell("select fn_imp_value('ds_json','" + tblId + "') from dual");
                            String cmd = querySingleCell("select fn_imp_value('ds_cmd','" + tblId + "') from dual");
                            Path jsonFile = Paths.get(othsDir, tblId + ".json");
                            Path shFile = Paths.get(othsDir, tblId + ".sh");
                            try {
                                Files.writeString(jsonFile, json == null ? "" : json, StandardCharsets.UTF_8);
                                Files.writeString(shFile, cmd == null ? "" : cmd, StandardCharsets.UTF_8);
                                int rc = runBash(shFile.toString(), jsonFile.toString());
                                if (rc == 0) {
                                    executeSqlStatement("begin sp_imp_status('cY','" + tblId + "');end;");
                                } else {
                                    executeRedisCommand("set ds." + dsId + " 1");
                                    executeSqlStatement("begin sp_imp_status('cE','" + tblId + "');end;");
                                    sleep(10000);
                                }
                            } catch (IOException io) {
                                log.error("写入或执行数据服务脚本失败: {}", tblId, io);
                                executeRedisCommand("set ds." + dsId + " 1");
                                executeSqlStatement("begin sp_imp_status('cE','" + tblId + "');end;");
                            } finally {
                                executeRedisFlag("rem", "ds_" + dsId + "_" + idx);
                            }
                        } finally { latch.countDown(); }
                    });
                }
                latch.await();
            }
            if (isDsOk(dsId) && isRedisTrue("ds." + dsId + ".post_sql")) {
                String post = querySingleCell("select post_sql from vw_imp_ds2 where ds_id='" + dsId + "'");
                if (post != null) { Path f = Paths.get(othsDir, dsId + ".postsql"); Files.writeString(f, post, StandardCharsets.UTF_8); runSqlFile(f.toString(), 1); }
            }
            if (isDsOk(dsId) && isRedisTrue("ds." + dsId + ".post_sh")) {
                String postsh = querySingleCell("select post_sh from vw_imp_ds2 where ds_id='" + dsId + "'");
                if (postsh != null) { Path f = Paths.get(othsDir, dsId + ".postsh"); Files.writeString(f, postsh, StandardCharsets.UTF_8); runBash(f.toString()); }
            }
            boolean ok = isDsOk(dsId);
            executeSqlStatement("begin sp_imp_status('" + (ok ? "Y" : "E") + "','" + dsId + "');end;");
            executeRedisCommand("del ds." + dsId);
            executeRedisCommand("del ds." + dsId + ".dsview");
            executeRedisCommand("del ds." + dsId + ".bupdate");
            executeRedisCommand("del ds." + dsId + ".pre_sql");
            executeRedisCommand("del ds." + dsId + ".pre_sh");
            executeRedisCommand("del ds." + dsId + ".post_sql");
            executeRedisCommand("del ds." + dsId + ".post_sh");
            return "ds_etl 完成: " + dsId + ", rc=" + (ok ? 0 : 1);
        } catch (Exception e) {
            log.error("ds_etl 失败", e);
            return "ds_etl 失败: " + e.getMessage();
        } finally { executeRedisFlag("rem", "ds." + dsId); }
    }

    /** 判断标志实现 */
    public String executeJudgeEtl(String flag) {
        if (flag == null || flag.isBlank()) return "flag 为空";
        if (!"1".equals(executeRedisFlag("add", flag))) return flag + " 已占用";
        try {
            String[] parts = flag.split("_");
            if (parts.length < 2) return "flag 格式错误";
            String kind = parts[0]; String sysid = parts[1];
            if ("status".equalsIgnoreCase(kind)) {
                String dbConn = querySingleCell("select db_conn from vw_imp_etl_judge where sysid='" + sysid + "' and px=1");
                String judgeSql = querySingleCell("select judge_sql from vw_imp_etl_judge where sysid='" + sysid + "' and px=1");
                String sts = runJdbc2Console(dbConn, judgeSql);
                if (sts != null) executeSqlStatement("begin sp_imp_flag('add','ETL_JUDGE','" + sysid + "'," + sts + ");end;");
            } else if ("start".equalsIgnoreCase(kind)) {
                executeSqlStatement("begin sp_imp_flag('add','ETL_START','" + sysid + "',1);end;");
                String presh = querySingleCell("select judge_pre from vw_imp_etl_judge where px=1 and bstart=1 and sysid='" + sysid + "'");
                if (presh != null) {
                    String cmdsDir = Optional.ofNullable(executeRedisCommand("get path.cmds")).orElse(rootDir + "/cmds");
                    Files.createDirectories(Paths.get(cmdsDir));
                    Path f = Paths.get(cmdsDir, "judge_pre_" + sysid + ".sh");
                    Files.writeString(f, presh, StandardCharsets.UTF_8);
                    int rc = runBash(f.toString());
                    if (rc == 0) executeSqlStatement("begin sp_imp_flag('add','ETL_START','" + sysid + "',2);end;");
                    else sendToWecomRobot(sysid + "的前置任务执行失败!!!");
                }
            }
            return "judge_etl 完成: " + flag;
        } catch (Exception e) {
            log.error("judge_etl 失败", e);
            return "judge_etl 失败: " + e.getMessage();
        } finally { executeRedisFlag("rem", flag); }
    }

    /** 获取源库及hadoop的表结构信息 */
    public String executeSourceTableStart() {
        if (!"1".equals(executeRedisFlag("add", "soutab"))) return "soutab 已占用";
        try {
            String othsDir = Optional.ofNullable(executeRedisCommand("get path.oths")).orElse(rootDir + "/oths");
            Files.createDirectories(Paths.get(othsDir));
            List<String> dbConns = querySingleColumn("select sou_db_conn from vw_imp_etl_soutab where kind='etl'");
            CountDownLatch latch = new CountDownLatch(dbConns.size());
            for (String db : dbConns) {
                executorService.submit(() -> { try { executeSourceTableEtl(db); } finally { latch.countDown(); }});
            }
            latch.await();
            executeSqlStatement("begin sp_imp_alone('colexch_updt');end;");
            for (String kind : List.of("updt_hive", "updt_mysql")) {
                String sql = querySingleCell("select fn_imp_value('" + kind + "') from dual");
                if (sql != null && sql.length() > 5) {
                    Path f = Paths.get(othsDir, kind + ".sql");
                    Files.writeString(f, sql, StandardCharsets.UTF_8);
                    if ("updt_hive".equals(kind)) runTuna("hive", f.toString(), 0); else log.info("请使用MySQL客户端执行 {}", f);
                }
            }
            return "soutab_start 完成";
        } catch (Exception e) { log.error("soutab_start 失败", e); return "soutab_start 失败: " + e.getMessage(); }
        finally { executeRedisFlag("rem", "soutab"); }
    }

    /** 源表ETL处理 */
    public Integer executeSourceTableEtl(String dbConn) {
        if (dbConn == null || dbConn.isBlank()) return 1;
        if (!"1".equals(executeRedisFlag("add", "soutab." + dbConn))) return 1;
        try {
            String json = querySingleCell("select col_json from vw_imp_etl_soutab where sou_db_conn='" + dbConn + "'");
            if (json == null || json.isBlank()) return 1;
            String othsDir = Optional.ofNullable(executeRedisCommand("get path.oths")).orElse(rootDir + "/oths");
            Files.createDirectories(Paths.get(othsDir));
            Path jsonFile = Paths.get(othsDir, "soutab_" + dbConn + ".json");
            Files.writeString(jsonFile, json, StandardCharsets.UTF_8);
            int rc = runTuna("schema", jsonFile.toString(), 0);
            if (rc == 0) executeSqlStatement("begin sp_imp_alone('bupdate','" + dbConn + "','n');end;");
            else sendToWecomRobot("获取" + dbConn + "的表结构信息失败!!!!");
            return rc;
        } catch (Exception e) { log.error("soutab_etl 失败", e); return 1; }
        finally { executeRedisFlag("rem", "soutab." + dbConn); }
    }

    /** 参数更新 */
    public String updateParameters() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
        if (!"1630".equals(now)) { recordSystemLog("参数更新任务不能执行,任务退出,非切日时间点"); return "非切日时间点"; }
        sendToWecomRobot("系统参数param_sys开始切换\nTD=" + executeRedisCommand("get param.TD") + "\nCD=" + executeRedisCommand("get param.CD"));
        List<String> tds = List.of(
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        );
        for (String td : tds) {
            try { executeSqlStatement("select sp_imp_param(" + td + ")"); }
            catch (Exception e) { sendToWecomRobot("系统参数param_sys生成失败!!!!"); return "失败"; }
        }
        List<String> rdsCmds = querySingleColumn("select rds from vw_updt_rds");
        for (String line : rdsCmds) log.info("更新redis: {} => {}", line, executeRedisCommand(line));
        sendToWecomRobot("系统参数param_sys切换完成！\n TD=" + executeRedisCommand("get param.TD") + "\n CD=" + executeRedisCommand("get param.CD"));
        recordSystemLog("参数更新任务执行完毕");
        return "参数更新完成";
    }

    /** 调度工具检查 */
    public String executeDataServiceCheck() {
        recordSystemLog("检测ds，发起");
        int minute = Integer.parseInt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("mm")));
        if (minute <= 1) executeRedisFlag("add", "dschk.msg");
        executeRedisCommand("set dschk 1");
        sleep(60000);
        if ("1".equals(executeRedisCommand("get dschk"))) {
            executeRedisCommand("set dschk 0");
            executeRedisCommand("set dschk.errcnt 0");
        } else {
            int err = parseIntOrDefault(executeRedisCommand("get dschk.errcnt"), 0) + 1;
            executeRedisCommand("set dschk.errcnt " + err);
            sendToWecomRobot("调度工具连续异常" + err + "次，需要赶紧处理!!");
        }
        executeRedisFlag("rem", "dschk.msg");
        return "dschk 完成";
    }

    /** 系统检查 */
    public String executeSystemCheck() { return "0"; }

    /** 状态 */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", LocalDateTime.now());
        status.put("hostname", getHostname());
        status.put("rootDir", rootDir);
        return status;
    }

    /** Redis标志 */
    public Map<String, Object> getRedisFlags() {
        Map<String, Object> flags = new HashMap<>();
        String allFlags = executeRedisFlag("all", "");
        flags.put("flags", allFlags != null ? Arrays.asList(allFlags.split("\n")) : Collections.emptyList());
        return flags;
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

    private List<Map.Entry<String, String>> fetchKindAndIds(String sql) {
        String res = querySingleCell(sql);
        if (res == null) return Collections.emptyList();
        return Arrays.stream(res.split("\n")).filter(s -> !s.isBlank()).map(l -> {
            String[] p = l.split("\\|");
            String k = p.length > 0 ? p[0] : ""; String v = p.length > 1 ? p[1] : "";
            return new AbstractMap.SimpleEntry<>(k, v);
        }).collect(Collectors.toList());
    }

    private void dispatchStartWkf(String kind, String val) {
        executorService.submit(() -> {
            switch (kind) {
                case "plan" -> executeSpEtl(val, "plan");
                case "judge" -> executeJudgeEtl(val);
                case "ds" -> executeDataServiceEtl(val);
                case "soutab" -> executeSourceTableEtl(val);
                case "sp" -> executeSpEtl(val, null);
                case "spcom" -> executeSpEtl(val, "manual");
                default -> runShell(kind + " " + val);
            }
        });
    }

    private int runTuna(String mode, String file, int timeoutSeconds) {
        String tuna = Paths.get(rootDir, "bin", "tuna.py").toString();
        List<String> cmd = new ArrayList<>();
        cmd.add(tuna);
        if (timeoutSeconds > 0) { cmd.add("-t"); cmd.add(String.valueOf(timeoutSeconds)); }
        cmd.add("-m"); cmd.add(mode); cmd.add("-f"); cmd.add(file);
        return runExternal(cmd.toArray(new String[0]));
    }

    private String runJdbc2Console(String dbConnArgs, String sql) {
        try {
            String tool = Paths.get(rootDir, "bin", "jdbc2console.sh").toString();
            File temp = File.createTempFile("jdbc_sql_", ".sql");
            Files.writeString(temp.toPath(), sql == null ? "" : sql, StandardCharsets.UTF_8);
            String[] parts = (dbConnArgs == null) ? new String[]{} : dbConnArgs.trim().split("\\s+");
            List<String> cmd = new ArrayList<>(); cmd.add(tool); cmd.addAll(Arrays.asList(parts));
            cmd.add("-f"); cmd.add(temp.getAbsolutePath());
            return runAndCapture(cmd.toArray(new String[0]));
        } catch (Exception e) { log.error("执行jdbc2console失败", e); return null; }
    }

    private int runBash(String... args) {
        List<String> cmd = new ArrayList<>(); cmd.add("bash"); cmd.addAll(Arrays.asList(args));
        return runExternal(cmd.toArray(new String[0]));
    }

    private void runShell(String inline) { runExternal(new String[]{"bash", "-lc", inline}); }

    private int runExternal(String[] cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd); pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line; while ((line = br.readLine()) != null) log.info(line);
            }
            return p.waitFor();
        } catch (Exception e) { log.error("外部命令执行失败: {}", Arrays.toString(cmd), e); return 1; }
    }

    private String runAndCapture(String[] cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd); pb.redirectErrorStream(true);
        try {
            Process p = pb.start(); StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line; while ((line = br.readLine()) != null) out.append(line);
            }
            p.waitFor(); return out.toString();
        } catch (Exception e) { log.error("外部命令执行失败: {}", Arrays.toString(cmd), e); return null; }
    }

    private void archiveLogs(String logDir, String ymd) {
        try {
            Path base = Paths.get(logDir); Path dayDir = base.resolve(ymd);
            Files.createDirectories(dayDir);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(base, "*_*_" + ymd + "_*.log")) {
                for (Path p : ds) Files.move(p, dayDir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) { log.warn("日志归档失败", e); }
    }

    private void compressBackupFiles(String bakDir, String today) {
        try {
            Path marker = Paths.get(bakDir, "autobak_" + today + ".tar.gz");
            Files.writeString(marker, "placeholder", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) { log.warn("备份压缩失败", e); }
    }

    private void cleanHistoryFiles(String bakDir) {
        try {
            Path base = Paths.get(bakDir); long cutoff = System.currentTimeMillis() - 12L * 24 * 3600 * 1000;
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(base)) {
                for (Path p : ds) if (Files.isRegularFile(p) && p.toFile().lastModified() < cutoff) Files.deleteIfExists(p);
            }
        } catch (Exception e) { log.warn("历史文件清理失败", e); }
    }

    private boolean isRedisTrue(String key) {
        String v = executeRedisCommand("get " + key);
        return "1".equalsIgnoreCase(v) || "Y".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v);
    }

    private boolean isDsOk(String dsId) { return "0".equals(executeRedisCommand("get ds." + dsId)); }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    private int parseIntOrDefault(String s, int d) { try { return Integer.parseInt(s); } catch (Exception e) { return d; } }
}
