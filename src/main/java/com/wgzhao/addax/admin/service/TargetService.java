package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
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
    private final EtlJourService jourService;

    public TaskResultDto createOrUpdateHiveTable(VwEtlTableWithSource etlTable)
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
        //  write to temporary file
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql);
        String sqlPath;
        try {
            sqlPath = FileUtils.writeToTempFile("hive_ddl_", createTableSql);
        }
        catch (IOException e) {
            log.warn("failed to write create table sql to temporary file", e);
            jourService.failJour(etlJour, e.getMessage());
            return TaskResultDto.failure("写入临时文件失败：" + e.getMessage(), 0);
        }
        String cmd = dictService.getHiveCli() + " -f " + sqlPath;
        TaskResultDto result = CommandExecutor.executeWithResult(cmd);
        if (result.isSuccess()) {
            jourService.successJour(etlJour);
            return TaskResultDto.success("创建或更新 Hive 表成功", 0);
        } else {
            log.warn("failed to create hive table for tid {}, command: {}, output: {}", etlTable.getId(), cmd, result.getMessage());
            jourService.failJour(etlJour, result.getMessage());
            return TaskResultDto.failure("创建或更新 Hive 表失败：" + result.getMessage(), 0);
        }
    }
}
