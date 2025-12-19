package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.text.MessageFormat;
import java.util.Objects;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;
import static com.wgzhao.addax.admin.common.HiveType.isHiveTypeCompatible;

@Service
@Slf4j
@AllArgsConstructor
public class TargetServiceWithHiveImpl
    implements TargetService
{

    private final DictService dictService;
    private final ColumnService columnService;
    private final EtlJourService jourService;
    private final SystemConfigService configService;

    private volatile DataSource hiveDataSource;

    @Override
    public Connection getHiveConnect()
    {
        if (hiveDataSource == null) {
            synchronized (this) {
                if (hiveDataSource == null) {
                    HiveConnectDto hiveConnectDto = configService.getHiveServer2();
                    log.info("try to load hive jdbc driver from {}", hiveConnectDto.driverPath());
                    try {
                        hiveDataSource = getHiveDataSourceWithConfig(hiveConnectDto);
                        return hiveDataSource.getConnection();
                    }
                    catch (SQLException | MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        try {
            return hiveDataSource.getConnection();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to get connection from Hive DataSource", e);
        }
    }

    @Override
    public DataSource getHiveDataSourceWithConfig(HiveConnectDto hiveConnectDto)
        throws MalformedURLException
    {

        File hiveJarFile = new File(hiveConnectDto.driverPath());
        URL[] jarUrls = new URL[] {hiveJarFile.toURI().toURL()};
        // 创建独立的类加载器
        URLClassLoader classLoader = new URLClassLoader(jarUrls, this.getClass().getClassLoader());

        // Set the context class loader
        Thread.currentThread().setContextClassLoader(classLoader);

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(hiveConnectDto.url());
        dataSource.setUsername(hiveConnectDto.username());
        dataSource.setPassword(hiveConnectDto.password());
        dataSource.setDriverClassName(hiveConnectDto.driverClassName());
        return dataSource;
    }

    /**
     * 为指定 Hive 表添加分区。
     * 对于非分区表（partName 为空），直接返回 true，不执行任何操作。
     *
     * @param taskId 采集任务 ID
     * @param db Hive 数据库名
     * @param table Hive 表名
     * @param partName 分区字段名（为空表示非分区表）
     * @param partValue 分区字段值
     * @return 是否添加成功
     */
    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        // 检查是否为非分区表
        if (partName == null || partName.trim().isEmpty()) {
            log.info("Table {}.{} is non-partitioned, skip partition operation", db, table);
            return true;
        }

        String sql = String.format("ALTER TABLE `%s`.`%s` ADD IF NOT EXISTS PARTITION (%s='%s')", db, table, partName, partValue);
        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
        try (Connection conn = getHiveConnect();
            Statement stmt = conn.createStatement()) {
            log.info("Add partition for {}.{}: {}", db, table, sql);
            stmt.execute(sql);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e) {
            log.error("Failed to add partition ", e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    /**
     * 创建或更新 Hive 目标表。
     * 包括建库、建表、分区、表属性等操作。
     * 支持分区表和非分区表：当 partName 为空时创建非分区表。
     *
     * @param etlTable 采集表视图对象
     * @return 是否创建/更新成功
     */
    @Override
    public boolean createOrUpdateHiveTable(VwEtlTableWithSource etlTable)
    {
        List<String> hiveColumns = columnService.getHiveColumnsAsDDL(etlTable.getId());

        String createDbSql = MessageFormat.format("create database if not exists `{0}` location ''{1}/{0}''",
            etlTable.getTargetDb(), dictService.getHdfsPrefix());

        // 检查是否为分区表
        boolean isPartitioned = etlTable.getPartName() != null && !etlTable.getPartName().trim().isEmpty();

        String createTableSql;
        if (isPartitioned) {
            // 分区表
            createTableSql = MessageFormat.format("""
                    create external table if not exists `{0}`.`{1}` (
                    {2}
                    ) comment ''{3}''
                    partitioned by ( `{4}` string )
                     stored as {5}
                     location ''{6}/{0}/{1}''
                     tblproperties (''external.table.purge''=''true'', ''discover.partitions''=''true'', ''orc.compress''=''{7}'', ''snappy.compress''=''{7}'')
                    """,
                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(),
                etlTable.getPartName(), dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), dictService.getHdfsCompress());
        }
        else {
            // 非分区表
            createTableSql = MessageFormat.format("""
                    create external table if not exists `{0}`.`{1}` (
                    {2}
                    ) comment ''{3}''
                     stored as {4}
                     location ''{5}/{0}/{1}''
                     tblproperties (''external.table.purge''=''true'', ''orc.compress''=''{6}'', ''snappy.compress''=''{6}'')
                    """,
                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(),
                dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), dictService.getHdfsCompress());
        }

        log.info("create table sql:\n{}", createTableSql);
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql);
        try (Connection conn = getHiveConnect();
            Statement stmt = conn.createStatement()) {
            stmt.execute(createDbSql);
            // 1) Check whether table exists before creation
            boolean existedBefore = hiveTableExists(stmt, etlTable.getTargetDb(), etlTable.getTargetTable());
            // Ensure DB and table exist (no-op if already there)
            stmt.execute(createTableSql);
            jourService.successJour(etlJour);

            // If table did not exist before, skip ALTER/diff logic on first creation
            if (!existedBefore) {
                log.info("Table {}.{} was newly created; skip alter diff on initial creation.", etlTable.getTargetDb(), etlTable.getTargetTable());
                return true;
            }

            // 2) ALTER mode: fetch current hive columns and apply diffs
            List<EtlColumn> desiredCols = columnService.getColumns(etlTable.getId());
            // build desired active map name -> EtlColumn (skip deleted placeholders)
            LinkedHashMap<String, EtlColumn> desiredActive = new LinkedHashMap<>();
            for (EtlColumn c : desiredCols) {
                String name = c.getColumnName();
                if (name != null && !name.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                    desiredActive.put(name, c);
                }
            }
            // fetch current hive columns via DESCRIBE
            LinkedHashMap<String, HiveCol> hiveCurrent = describeHiveColumns(stmt, etlTable.getTargetDb(), etlTable.getTargetTable());

            // generate DDLs
            ArrayList<String> alterDDLs = new ArrayList<>();
            // additions
            for (var entry : desiredActive.entrySet()) {
                String name = entry.getKey().toLowerCase();
                EtlColumn c = entry.getValue();
                HiveCol hc = hiveCurrent.get(name);
                String comment = normalizeComment(c.getColComment());
                String typeFull = c.getTargetTypeFull();
                String newComment = comment.isEmpty() ? "" : " COMMENT '" + comment + "'";
                if (hc == null) {
                    alterDDLs.add(String.format("ALTER TABLE `%s`.`%s` ADD COLUMNS ( `%s` %s%s )",
                        etlTable.getTargetDb(), etlTable.getTargetTable(), name, typeFull,
                        newComment));
                }
                else {
                    // compare type; if different, use CHANGE COLUMN
                    boolean typeDiff = !Objects.equals(hc.type, typeFull);
                    // only change type when current Hive type is NOT compatible with desired type
                    if (typeDiff && !isHiveTypeCompatible(hc.type, typeFull)) {
                        alterDDLs.add(String.format("ALTER TABLE `%s`.`%s` CHANGE COLUMN `%s` `%s` %s%s",
                            etlTable.getTargetDb(), etlTable.getTargetTable(), name, name, typeFull,
                            newComment));
                    }
                }
            }

            // execute ALTER sequentially
            for (String ddl : alterDDLs) {
                EtlJour j = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, ddl);
                try {
                    log.info("apply hive alter: {}", ddl);
                    stmt.execute(ddl);
                    jourService.successJour(j);
                }
                catch (SQLException ex) {
                    log.error("Failed to apply alter for {}.{}: {}", etlTable.getTargetDb(), etlTable.getTargetTable(), ddl, ex);
                    jourService.failJour(j, ex.getMessage());
                    // continue trying next statements; optionally could break depending on config
                }
            }
            return true;
        }
        catch (SQLException e) {
            log.warn("Failed to create or update hive table ({}.{}) ", etlTable.getTargetDb(), etlTable.getTargetTable(), e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    private static String normalizeComment(String v)
    {
        if (v == null) {
            return "";
        }
        String c = v.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return c.replace("'", "''");
    }

    // minimal column struct from DESCRIBE output
    private static class HiveCol
    {
        String name;
        String type;
        String comment;

        HiveCol(String n, String t, String c)
        {
            this.name = n;
            this.type = t;
            this.comment = c;
        }
    }

    /**
     * Parse DESCRIBE `db`.`table` output to current columns map (non-partition columns only).
     */
    private LinkedHashMap<String, HiveCol> describeHiveColumns(Statement stmt, String db, String table)
        throws SQLException
    {
        LinkedHashMap<String, HiveCol> cols = new LinkedHashMap<>();
        String sql = String.format("DESCRIBE `%s`.`%s`", db, table);
        try (var rs = stmt.executeQuery(sql)) {
            boolean inCols = true;
            while (rs.next()) {
                String col = rs.getString(1);
                String type = rs.getString(2);
                String comment = rs.getString(3);
                if (col == null) {
                    continue;
                }
                col = col.trim();
                if (col.isEmpty()) {
                    // blank line separates columns from partition columns in DESCRIBE
                    inCols = false;
                    continue;
                }
                if (!inCols) {
                    // skip partition section
                    continue;
                }
                // skip headers like # col_name
                if (col.startsWith("#")) {
                    continue;
                }
                cols.put(col, new HiveCol(col, type == null ? "" : type.trim(), comment == null ? "" : comment.trim()));
            }
        }
        return cols;
    }

    /**
     * Check whether hive table exists by SHOW TABLES IN db LIKE 'table'
     */
    private boolean hiveTableExists(Statement stmt, String db, String table)
    {
        String sql = String.format("SHOW TABLES IN `%s` LIKE '%s'", db, table);
        try (var rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
        catch (SQLException e) {
            log.error("Failed to check hive table existence for {}.{} ", db, table, e);
            return false;
        }
    }
}
