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
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;

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
    private JdbcTemplate jdbcTemplate;

    @Resource
    private RedisTemplate<String, ?> redisTemplate;

    @Resource
    private RestTemplate restTemplate;

    private String hiveJdbcUrl;

    private String hivejdbcUsername;

    private String hivejdbcPassword;

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
        // Step 1: Process each sou_db_conn value
        log.info("Fetching sou_db_conn values");
        List<Map<String, Object>> maps = jdbcTemplate.queryForList("select sou_db_conn from vw_imp_etl_soutab where kind='etl'");
        if (maps.isEmpty()) {
            log.warn("No sou_db_conn values found, skipping ETL update");
            return;
        }
        for (Map<String, Object> map : maps) {
            String dbConn = (String) map.get("sou_db_conn");
            if (dbConn != null && !dbConn.isEmpty()) {
                log.info("Processing sou_db_conn: {}", dbConn);
                soutabEtl(dbConn);
            }
            else {
                log.warn("Found empty sou_db_conn, skipping");
            }
        }
        boolean updtResult = jdbcTemplate.queryForObject("select sp_imp_alone('colexch_updt')", Boolean.class);
        if (!updtResult) {
            log.error("首次刷新对比表失败: sp_imp_alone('colexch_updt')");
            return;
        }
//            if (!invokeProcedure(connection, "sp_imp_alone('colexch_updt')")) {
//                log.error("首次刷新对比表失败: sp_imp_alone('colexch_updt')");
//                return;
//            }

        // Step 2: Execute update procedures for hive and mysql
        for (String kind : new String[] {"updt_hive", "updt_mysql"}) {
            processUpdateByKind(kind);
        }

        // Step 3: Refresh hadoop table schema and procedures
        if (redisTemplate.hasKey("soutab.task")) {
            soutabEtl("hadoop");
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

        cacheUtil.del("soutab");
        log.info("tableSchemaUpdate finished and flag 'soutab' released");
    }

    /**
     * start ETL task
     * the corresponding shell module is sp_init function
     */
    @Async
    public void startEtl()
    {
        if (!cacheUtil.tryLock("sp_init", "lock", 3)) {
            log.warn("sp_init is already running, skipping");
            return;
        }
        funcHelper.fnImpValue("etl_end");
    }

    private void processUpdateByKind(String kind)
    {
        log.info("Processing update for kind: {}", kind);

//        String updateSql = funcHelper.fnImpValue(kind);
        String updateSql = jdbcTemplate.queryForObject("select fn_imp_value('" + kind + "')", String.class);
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

    private void executeHiveUpdate(String sql)
    {
        try {
            Path tempPath = Files.createTempFile("updt", ".sql");
            Files.writeString(tempPath, sql);
            log.info("Executing hive update with temporary file: {}", tempPath);
            Process process = Runtime.getRuntime().exec(new String[] {"hive", "-f", tempPath.toString()});
            process.waitFor();
            Files.deleteIfExists(tempPath);
            log.info("Hive update executed successfully");
        } catch (IOException | InterruptedException e) {
            log.error("Error executing hive update", e);
            Thread.currentThread().interrupt(); // Restore interrupted status
        }
        catch (Exception e) {
            log.error("Unexpected error during hive update", e);
        }
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

    private boolean soutabEtl(String kind)
    {
        log.info("Starting update process for kind: {}", kind);
        if (!cacheUtil.tryLock("soutab." + kind, "lock", 3)) {
            log.warn("soutab.{} flag is already set, skipping update", kind);
            return false;
        }
        String colJson = jdbcTemplate.queryForObject("select col_json from vw_imp_etl_soutab where sou_db_conn='" + kind + "'", String.class);
        if (colJson == null || colJson.length() < 100) {
            log.warn("update json for kind: {} is too short", kind);
            return false;
        }
        log.info("update json for kind {}: {}", kind, colJson);
        log.info("Executing table copy for kind: {}", kind);
        if (DbUtil.tableCopy(colJson)) {
            log.info("Table copy executed successfully for kind: {}, set the bupdate to n", kind);
            procedureHelper.spImpAlone("bupdate", kind, "n");
            return true;
        }
        else {
            return false;
        }
    }
}