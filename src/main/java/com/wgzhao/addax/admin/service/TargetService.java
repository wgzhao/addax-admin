package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 采集任务目标管理服务。
 * 主要负责 Hadoop 目标表的创建、分区管理、元数据更新等操作。
 * 包括 Hive 表的创建/更新、分区添加等功能。
 */
@Service
@Slf4j
public class TargetService
{
    @Autowired
    @Qualifier("hiveDataSource")
    private  DataSource hiveDataSource;

    @Autowired
    private  DictService dictService;

    @Autowired
    private  ColumnService columnService;

    @Autowired
    private  EtlJourService jourService;

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
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        String sql = String.format("ALTER TABLE %s.%s ADD IF NOT EXISTS PARTITION (%s=%s)", db, table, partName, partValue);
        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
        try (Connection conn = hiveDataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            log.info("Add partition for {}.{}: {}", db, table, sql);
            stmt.execute(sql);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e) {
            log.error("Failed to add partition for {}.{}: {}", db, table, sql, e);
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
    public boolean createOrUpdateHiveTable(VwEtlTableWithSource etlTable)
    {
        List<String> hiveColumns = columnService.getHiveColumnsAsDDL(etlTable.getId());
        String createTableSql = """
                create database if not exists `%s` location '%s/%s';
                create external table if not exists `%s`.`%s` (
                %s
                ) comment '%s'
                partitioned by ( `%s` string )
                 stored as %s
                 location '%s/%s/%s'
                 tblproperties ('external.table.purge'='true', 'discover.partitions'='true', 'orc.compress'='%s', 'snappy.compress'='%s')
                """.formatted(etlTable.getTargetDb(), dictService.getHdfsPrefix(), etlTable.getTargetDb(),
                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(), etlTable.getPartName(),
                dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), etlTable.getTargetDb(), etlTable.getTargetTable(),
                dictService.getHdfsCompress(), dictService.getHdfsCompress()
        );
        log.info("create table sql:\n{}", createTableSql);
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql);
        try (Connection conn = hiveDataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e) {
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }
}
