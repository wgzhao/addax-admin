package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.event.SourceUpdatedEvent;
import com.wgzhao.addax.admin.model.*;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
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
import static com.wgzhao.addax.admin.common.Constants.quoteIfNeeded;

/**
 * 采集任务内容服务类，负责采集任务的模板生成与更新等相关操作
 */
@Service
@Slf4j
@AllArgsConstructor
public class JobContentService {

    private final EtlJobRepo jobRepo;
    private final DictService dictService;
    private final ColumnService columnService;
    private final EtlJourService jourService;
    private final VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;
    private final SystemConfigService configService;

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

        Map<String, String> values = new HashMap<>();
        values.put("reader", fillRdbmsReaderJob(etlTable));
        values.put("writer", fillHdfsWriterJob(etlTable));

        String jobTemplate = dictService.getRdbms2HdfsJobTemplate();
        StringSubstitutor substitutor = new StringSubstitutor(values);
        String job = substitutor.replace(jobTemplate);

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
        jourService.successJour(etlJour);
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable());
        return TaskResultDto.success("更新采集任务模板成功", 0);
    }

    private String fillRdbmsReaderJob(VwEtlTableWithSource vTable) {
        String template = dictService.getRdbmsReaderTemplate();

        String kind = DbUtil.getKind(vTable.getUrl());
        Map<String, String> values = new HashMap<>();

        values.put("name", kind + "reader");
        values.put("username", vTable.getUsername());
        values.put("password", vTable.getPass() == null ? "" : vTable.getPass());
        values.put("jdbcUrl", vTable.getUrl());
        values.put("where", vTable.getFilter());
        values.put("autoPk", String.valueOf(vTable.getAutoPk()));
        values.put("splitPk", vTable.getSplitPk() == null ? "" : vTable.getSplitPk());
        values.put("fetchSize", "20480");
        Constants.DbType dbType = DbUtil.getDbType(vTable.getUrl());
        if (dbType == Constants.DbType.POSTGRESQL) {
            values.put("table", quoteIfNeeded(vTable.getSourceTable(), dbType));
        } else {
            values.put("table", quoteIfNeeded(vTable.getSourceDb(), dbType) + "." + quoteIfNeeded(vTable.getSourceTable(), dbType));
        }

        // 处理列信息
        List<EtlColumn> columnList = columnService.getColumns(vTable.getId());
        List<String> srcColumns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                // 被标记为删除的字段，那么使用 null 来填充该字段
                srcColumns.add("\"NULL\"");
            } else {
                // 如果列名是关键字，则还需要加上引号
                srcColumns.add("\"" + quoteIfNeeded(columnName, dbType) + "\"");
            }
        }
        values.put("column", String.join(", ", srcColumns));
        StringSubstitutor substitutor = new StringSubstitutor(values);
        return substitutor.replace(template);
    }

    private String fillHdfsWriterJob(VwEtlTableWithSource vTable) {

        Map<String, String> values = new HashMap<>();
        values.put("compress", vTable.getCompressFormat());
        values.put("fileType", vTable.getStorageFormat());

        // 处理列信息
        String columnJson = getHdfsWriteColumns(vTable, values);
        values.put("column", columnJson);

        // hdfs path 的前缀路径不应该在模板中填写，而应该从配置中获取
        Path hdfsPath = Paths.get(configService.getHdfsPrefix(), vTable.getTargetDb(), vTable.getTargetTable());

        if (!vTable.getPartName().isEmpty()) {
            hdfsPath = hdfsPath.resolve(vTable.getPartName() + "=${logdate}");
        }

        values.put("path", hdfsPath.toString());
        // 所有变量都需要处理，以防止模板替换错误

        String template = dictService.getHdfsWriterTemplate();
        StringSubstitutor substitutor = new StringSubstitutor(values);

        return substitutor.replace(template);
    }

    private String getHdfsWriteColumns(VwEtlTableWithSource vTable, Map<String, String> values) {
        List<EtlColumn> columnList = columnService.getColumns(vTable.getId());
        List<Map<String, String>> columns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
            Map<String, String> targetColumn = new HashMap<>();
            targetColumn.put("type", etlColumn.getTargetTypeFull());
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                // 目标表字段名还是正常的字段名
                targetColumn.put("name", columnName.substring(DELETED_PLACEHOLDER_PREFIX.length()));
            } else {
                targetColumn.put("name", columnName);
            }
            columns.add(targetColumn);
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(columns);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("column 转换为 JSON 失败", e);
        }
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
