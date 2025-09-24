package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import com.wgzhao.addax.admin.utils.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
@AllArgsConstructor
public class TargetService
{

    private final DictService dictService;
    private final ColumnService columnService;

    public boolean createOrUpdateHiveTable(EtlTable etlTable)
    {
        List<String> hiveColumns = columnService.getHiveColumns(etlTable.getId());
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
        //  write to temporary file
        String sqlPath;
        try {
            sqlPath = FileUtils.writeToTempFile("hive_ddl_", createTableSql);
        } catch (IOException e) {
            log.warn("failed to write create table sql to temporary file", e);
            return false;
        }
        String cmd = dictService.getHiveCli() + " -f " + sqlPath;
        CommandExecutor.CommandResult result = CommandExecutor.executeForOutput(cmd);
        if (result.exitCode() != 0) {
            log.warn("failed to create hive table for tid {}, command: {}, output: {}", etlTable.getId(), cmd, result.output());
            return false;
        }
        return true;
    }
}
