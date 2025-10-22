package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.HdfsWriterTemplate;
import com.wgzhao.addax.admin.dto.RdbmsReaderTemplate;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.event.SourceUpdatedEvent;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJob;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

/**
 * 采集任务内容服务类，负责采集任务的模板生成与更新等相关操作
 */
@Service
@Slf4j
public class JobContentService {
    @Autowired
    private EtlJobRepo jobRepo;

    @Autowired
    private DictService dictService;

    @Autowired
    private ColumnService columnService;

    @Autowired
    private SystemConfigService configService;

    @Autowired
    private EtlJourService jourService;

    @Autowired
    private VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;

    /**
     * 获取指定采集表的采集任务模板内容
     *
     * @param tid 采集表ID
     * @return 采集任务模板内容（JSON字符串），若不存在则返回null
     */
    public String getJobContent(long tid) {
        return jobRepo.findById(tid).map(EtlJob::getJob).orElse(null);
    }

    /**
     * 更新采集任务的json模板
     * 扫描tb_imp_etl任务，生成addax采集需要的json模板，并写入tb_imp_etl_job表
     * 可定期运行，确保tb_imp_etl_job表中的json内容最新
     *
     * @param etlTable 采集表视图对象
     * @return 任务结果
     */
    public TaskResultDto updateJob(VwEtlTableWithSource etlTable) {
        if (etlTable == null) {
            return TaskResultDto.failure("没有指定采集任务", 0);
        }
        log.info("准备更新表 {}.{}({}) 的采集任务模板", etlTable.getTargetDb(), etlTable.getTargetTable(), etlTable.getId());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.ADDAX_JOB, null);
        String kind = DbUtil.getKind(etlTable.getUrl());
        RdbmsReaderTemplate readerTemplate = new RdbmsReaderTemplate();
        readerTemplate.setName(kind + "reader");
        readerTemplate.setUsername(etlTable.getUsername());
        readerTemplate.setPassword(etlTable.getPass());
        readerTemplate.setJdbcUrl(etlTable.getUrl());
        readerTemplate.setWhere(etlTable.getFilter());
        // 这里对源 DB 和 TABLE 做了 quote，用于处理不规范命名的问题，比如 mysql 中的关键字作为表名等 ，库名包含中划线(-)
        // TODO 这里直接使用 ` 来做 quote，可能不适用于所有数据库，比如 Oracle 需要使用 " 来做 quote
        readerTemplate.setTable("`" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "`");


        List<EtlColumn> columnList = columnService.getColumns(etlTable.getId());
        List<String> srcColumns = new ArrayList<>();
        List<Map<String, String>> destColumns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
//            String targetColumn = typeTemplate.formatted(columnName, etlColumn.getTargetTypeFull());
            Map<String, String> targetColumn = new HashMap<>();
            targetColumn.put("type", etlColumn.getTargetTypeFull());

            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                // 被标记为删除的字段，那么使用 null 来填充该字段
                srcColumns.add("\"NULL\"");
                // 目标表字段名还是正常的字段名
                targetColumn.put("name", columnName.substring(DELETED_PLACEHOLDER_PREFIX.length()));
            } else {
                targetColumn.put("name", columnName);
                srcColumns.add("\"" + columnName + "\"");
            }
            destColumns.add(targetColumn);
        }
        readerTemplate.setColumn(srcColumns);

        HdfsWriterTemplate hdfsWriterTemplate = new HdfsWriterTemplate();
        hdfsWriterTemplate.setColumn(destColumns);
        hdfsWriterTemplate.setCompress(etlTable.getCompressFormat());
        hdfsWriterTemplate.setFileType(etlTable.getStorageFormat());

        Map<String, Object> hdfsConfig = dictService.getHadoopConfig();

        hdfsWriterTemplate.setDefaultFS(hdfsConfig.getOrDefault("defaultFS", "").toString());
        // col_idx = 1000 的字段为分区字段，不参与 select
        //TODO 路径结合应该考虑尾部 / 的问题
        Path hdfsPath = Paths.get(hdfsConfig.getOrDefault("hdfsPrefix", "/ods").toString(),
                etlTable.getTargetDb(), etlTable.getTargetTable());

        if (!etlTable.getPartName().isEmpty()) {
            hdfsPath = hdfsPath.resolve(etlTable.getPartName() + "=${logdate}");
        }
        hdfsWriterTemplate.setPath(hdfsPath.toString());
        if (hdfsConfig.getOrDefault("enableKerberos", "false").toString().equals("true")) {
            hdfsWriterTemplate.setHaveKerberos(true);
            hdfsWriterTemplate.setKerberosKeytabFilePath(
                    hdfsConfig.getOrDefault("kerberosKeytabFilePath", "").toString());
            hdfsWriterTemplate.setKerberosPrincipal(
                    hdfsConfig.getOrDefault("kerberosPrincipal", "").toString());
        }
        boolean enableHA = (boolean) hdfsConfig.getOrDefault("enableHA", false);
        hdfsWriterTemplate.setEnableHA(enableHA);
        if (enableHA) {
            String hdfsSitePath = hdfsConfig.getOrDefault("hdfsSitePath", "").toString();
            if (!hdfsSitePath.isEmpty()) {
                hdfsWriterTemplate.setHdfsSitePath(hdfsSitePath);
            } else {
                hdfsWriterTemplate.setHadoopConfig(hdfsConfig.get("hadoopConfig").toString());
            }
        }

        String job = """
                {
                  "job": {
                    "content": {
                        "reader": %s,
                        "writer": %s,
                      "setting": {
                        "speed": {
                          "batchSize": 20480,
                          "bytes": -1,
                          "channel": 1
                        }
                      }
                    }
                  }
                }
                """.formatted(readerTemplate.toJson(), hdfsWriterTemplate.toJson());

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
        jourService.successJour(etlJour);
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable());
        return TaskResultDto.success("更新采集任务模板成功", 0);
    }

    /**
     * 根据表ID删除对应的采集任务
     *
     * @param tableId 表ID
     */
    public void deleteByTid(long tableId) {
        jobRepo.deleteById(tableId);
    }

    /**
     * 根据数据源ID异步更新相关的采集任务
     *
     * @param sid 数据源ID
     */
    // 根据数据源 ID 更新相关的任务
    @Async
    public void updateJobBySourceId(int sid) {
        vwEtlTableWithSourceRepo.findBySidAndEnabledTrueAndStatusNot(sid, TableStatus.EXCLUDE_COLLECT)
                .forEach(this::updateJob);
    }

    @EventListener
    public void handleSourceUpdatedEvent(SourceUpdatedEvent event) {
        if (event.isConnectionChanged()) {
            updateJobBySourceId(event.getSourceId());
        }
    }
}
