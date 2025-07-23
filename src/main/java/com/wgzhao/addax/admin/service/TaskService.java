package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.utils.CacheUtil;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.FuncHelper;
import com.wgzhao.addax.admin.utils.ProcedureHelper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Service
@Slf4j
public class TaskService
{

    @Resource
    private CacheUtil cacheUtil;

    @Autowired
    private ProcedureHelper procedureHelper;

    @Autowired
    private FuncHelper funcHelper;

    @Autowired
    private DataSource dataSource;

    @Resource
    private RedisTemplate<String, ?> redisTemplate;

    @Resource
    private RestTemplate restTemplate;

    @Value("${hive.metastore.jdbc.url}")
    private String hiveJdbcUrl;

    @Value("${hive.metastore.jdbc.username}")
    private String hivejdbcUsername;

    @Value("${hive.metastore.jdbc.password}")
    private String hivejdbcPassword;

    @Value("${hive.metastore.jdbc.driver-class-name}")
    private String hivejdbcDriverClassName;

    /**
     * Asynchronously updates table schema.
     */
    @Async
    public void tableSchemaUpdate()
    {
        log.info("Begin table schema update");
        if (!cacheUtil.tryLock("soutab", "lock", 3)) {
            log.warn("tableSchemaUpdate aborted: 'soutab' flag is already set");
            return;
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // Step 1: Process each sou_db_conn value
            log.info("Fetching sou_db_conn values");
            try (ResultSet rs = statement.executeQuery("select sou_db_conn from vw_imp_etl_soutab where kind='etl'")) {
                while (rs.next()) {
                    String dbConn = rs.getString(1);
                    soutabEtl(connection, dbConn);
                }
            }

            if (!procedureHelper.spImpAlone("colexch_updt")) {
                log.error("首次刷新对比表失败: sp_imp_alone('colexch_updt')");
                return ;
            }
//            if (!invokeProcedure(connection, "sp_imp_alone('colexch_updt')")) {
//                log.error("首次刷新对比表失败: sp_imp_alone('colexch_updt')");
//                return;
//            }

            // Step 2: Execute update procedures for hive and mysql
            for (String kind : new String[] {"updt_hive", "updt_mysql"}) {
                processUpdateByKind(statement, kind);
            }

            // Step 3: Refresh hadoop table schema and procedures
            if (redisTemplate.hasKey("soutab.task")) {
                soutabEtl(connection, "hadoop");
                if (!procedureHelper.spImpAlone("colexch_updt")) {
                    log.error("Procedure sp_imp_alone('colexch_updt') failed");
                }
                cacheUtil.del("soutab.task");
            }

            // Step 4: Update status and refresh the diff table
            if (!procedureHelper.spImpAlone("bupdate", "N")) {
                log.error("Procedure sp_imp_alone('bupdate','N') failed");
            }
            else {
                log.info("Procedure sp_imp_alone('bupdate','N') executed successfully");
            }
        }
        catch (SQLException | IOException | InterruptedException e) {
            log.error("Exception during table schema update", e);
        }
        finally {
            cacheUtil.del("soutab");
            log.info("tableSchemaUpdate finished and flag 'soutab' released");
        }
    }

    /**
     * start ETL task
     * the corresponding shell module is sp_init function
     */
    @Async
    public void startEtl() {
        if (! cacheUtil.tryLock("sp_init", "lock", 3)) {
            log.warn("sp_init is already running, skipping");
            return;
        }

        funcHelper.fnImpValue("etl_end");

    }

    private void processUpdateByKind(Statement statement, String kind)
            throws SQLException, IOException, InterruptedException
    {
        log.info("Processing update for kind: {}", kind);
        try (ResultSet rs = statement.executeQuery("select fn_imp_value('" + kind + "') from dual")) {
            if (!rs.next()) {
                log.warn("No update SQL returned for kind: {}", kind);
                return;
            }
            String updateSql = rs.getString(1);
            if (updateSql == null || updateSql.length() < 100) {
                log.warn("Update SQL for kind: {} is too short", kind);
                return;
            }
            // Set temporary flag
            cacheUtil.tryLock("soutab.task", "lock", 3);
            ;
            if ("updt_hive".equals(kind)) {
                executeHiveUpdate(updateSql);
            }
            else {
                executeMetastoreUpdate(updateSql, kind);
            }
        }
    }

    private void executeHiveUpdate(String sql)
            throws IOException, InterruptedException
    {
        Path tempPath = Files.createTempFile("updt", ".sql");
        Files.writeString(tempPath, sql);
        log.info("Executing hive update with temporary file: {}", tempPath);
        Process process = Runtime.getRuntime().exec(new String[] {"hive", "-f", tempPath.toString()});
        process.waitFor();
        Files.deleteIfExists(tempPath);
        log.info("Hive update executed successfully");
    }

    private void executeMetastoreUpdate(String sql, String kind)
    {
        try (Connection conn = DbUtil.getConnect(hiveJdbcUrl, hivejdbcUsername, hivejdbcPassword);
                Statement stmt = conn != null ? conn.createStatement() : null) {
            if (conn == null || stmt == null) {
                log.warn("Failed to connect to hive metastore for kind: {}", kind);
                return;
            }
            stmt.execute(sql);
            log.info("Hive metastore update executed successfully for kind: {}", kind);
        }
        catch (SQLException e) {
            log.error("Error executing hive metastore update for kind: {}", kind, e);
        }
    }

    private boolean soutabEtl(Connection connection, String kind)
    {
        log.info("Starting update process for kind: {}", kind);
        if (!cacheUtil.tryLock("soutab." + kind, "lock", 3)) {
            log.warn("soutab.{} flag is already set, skipping update", kind);
            return false;
        }
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("select col_json from vw_imp_etl_soutab where sou_db_conn='" + kind + "'")) {
            if (!rs.next()) {
                log.warn("No update json found for kind: {}", kind);
                return false;
            }
            String colJson = rs.getString(1);
            if (colJson == null || colJson.length() < 100) {
                log.warn("update json for kind: {} is too short", kind);
                return false;
            }
            log.info("update json for kind {}: {}", kind, colJson);
            log.info("Executing table copy for kind: {}", kind);
            if (DbUtil.tableCopy(colJson)) {
                log.info("Table copy executed successfully for kind: {}, set the bupdate to n", kind);
                invokeProcedure(connection, "sp_imp_alone('bupdate','" + kind + "','n')");
                return true;
            } else {
                return false;
            }

        }
        catch (SQLException e) {
            log.error("Error processing update for kind: {}", kind, e);
            return false;
        }
    }

    private boolean invokeProcedure(Connection connection, String procedureName)
    {
        String procCall = "{call " + procedureName + "}";
        log.info("Invoking procedure: {}", procedureName);
        try (CallableStatement cstmt = connection.prepareCall(procCall)) {
            cstmt.execute();
            return true;
        }
        catch (SQLException e) {
            return false;
        }
    }

    private boolean executeScheduler(String comp)
    {
        String url = "http://etl01:12345/dolphinscheduler/projects/10691104512992/executors/start-process-instance";
        String token = "de27aefdf8f0392ddab7c2144af67ab0";
        String payload = String.format(
                "failureStrategy=END&processDefinitionCode=10691166416992&processInstancePriority=MEDIUM&scheduleTime=&warningGroupId=0&warningType=NONE&startParams={\"comt\":\"%s\"}",
                comp);
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", token);
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Scheduler response: {}", response.getBody());
                return true;
            }
            else {
                log.error("Failed to execute scheduler; status code: {}", response.getStatusCode());
                return false;
            }
        }
        catch (Exception e) {
            log.error("Exception while executing scheduler for comp: {}", comp, e);
            return false;
        }
    }
}