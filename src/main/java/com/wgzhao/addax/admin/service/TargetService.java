package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import com.wgzhao.addax.admin.utils.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 采集任务入口管理器
 * 这里主要是针对采集到 Hadoop 上的目标做一些管理工作，比如说：
 * 1. 目标表的创建和删除
 * 2. 目标表的分区管理
 * 3. 目标表的数据清理和归档
 * 4. 目标表的源数据更新
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
