//package com.wgzhao.addax.admin.service.impl;
//
//import com.wgzhao.addax.admin.common.JourKind;
//import com.wgzhao.addax.admin.config.HiveCmdCondition;
//import com.wgzhao.addax.admin.dto.TaskResultDto;
//import com.wgzhao.addax.admin.model.EtlJour;
//import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
//import com.wgzhao.addax.admin.service.ColumnService;
//import com.wgzhao.addax.admin.service.DictService;
//import com.wgzhao.addax.admin.service.EtlJourService;
//import com.wgzhao.addax.admin.service.TargetService;
//import com.wgzhao.addax.admin.utils.CommandExecutor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Conditional;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//@Conditional(HiveCmdCondition.class)
//@Slf4j
//public class TargetServiceWithCmdImpl
//        implements TargetService
//{
//    @Autowired
//    private EtlJourService jourService;
//
//    @Autowired
//    private ColumnService columnService;
//
//    @Autowired
//    private DictService dictService;
//
//    @Override
//    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
//    {
//        String sql = String.format("ALTER TABLE %s.%s ADD IF NOT EXISTS PARTITION (%s=%s)", db, table, partName, partValue);
//        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
//        String cmd = "hive -e \"" + sql + "\"";
//        log.info("add partition cmd: {}", cmd);
//        TaskResultDto taskResultDto = CommandExecutor.executeWithResult(cmd);
//        if (taskResultDto.isSuccess()) {
//            jourService.successJour(etlJour);
//            return true;
//        } else {
//            jourService.failJour(etlJour, taskResultDto.getMessage());
//            return false;
//        }
//    }
//
//    @Override
//    public boolean createOrUpdateHiveTable(VwEtlTableWithSource etlTable)
//    {
//        List<String> hiveColumns = columnService.getHiveColumnsAsDDL(etlTable.getId());
//        String createTableSql = """
//                create database if not exists `%s` location '%s/%s';
//                create external table if not exists `%s`.`%s` (
//                %s
//                ) comment '%s'
//                partitioned by ( `%s` string )
//                 stored as %s
//                 location '%s/%s/%s'
//                 tblproperties ('external.table.purge'='true', 'discover.partitions'='true', 'orc.compress'='%s', 'snappy.compress'='%s')
//                """.formatted(etlTable.getTargetDb(), dictService.getHdfsPrefix(), etlTable.getTargetDb(),
//                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(), etlTable.getPartName(),
//                dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), etlTable.getTargetDb(), etlTable.getTargetTable(),
//                dictService.getHdfsCompress(), dictService.getHdfsCompress()
//        );
//        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql);
//        String cmd = "hive -e \"" + createTableSql + "\"";
//        log.info("create table cmd: {}", cmd);
//        TaskResultDto taskResultDto = CommandExecutor.executeWithResult(cmd);
//        if (taskResultDto.isSuccess()) {
//            jourService.successJour(etlJour);
//            return true;
//        } else {
//            jourService.failJour(etlJour, taskResultDto.getMessage());
//            return false;
//        }
//    }
//}
