package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.*;
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
import java.util.List;
import java.text.MessageFormat;

@Service
@Slf4j
public class TargetServiceWithHiveImpl
        implements TargetService
{

    @Autowired
    private DictService dictService;

    @Autowired
    private ColumnService columnService;

    @Autowired
    private EtlJourService jourService;

    @Autowired
    private SystemConfigService configService;

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
     *
     * @param taskId 采集任务ID
     * @param db Hive数据库名
     * @param table Hive表名
     * @param partName 分区字段名
     * @param partValue 分区字段值
     * @return 是否添加成功
     */
    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        String sql = String.format("ALTER TABLE %s.%s ADD IF NOT EXISTS PARTITION (%s='%s')", db, table, partName, partValue);
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
        String createTableSql = MessageFormat.format("""
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

        log.info("create table sql:\n{}", createTableSql);
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql);
        try (Connection conn = getHiveConnect();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createDbSql);
            stmt.execute(createTableSql);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e) {
            log.warn("Failed to create or update hive table ", e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }
}
