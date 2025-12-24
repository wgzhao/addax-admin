package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.event.SourceUpdatedEvent;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJob;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_PART_FORMAT;
import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;
import static com.wgzhao.addax.admin.common.Constants.SPECIAL_FILTER_PLACEHOLDER;
import static com.wgzhao.addax.admin.utils.DbUtil.getDbType;
import static com.wgzhao.addax.admin.utils.DbUtil.quoteIfNeeded;

/**
 * 采集任务内容服务类，负责采集任务的模板生成与更新等相关操作
 */
@Service
@Slf4j
@AllArgsConstructor
public class JobContentService
{

    private final EtlJobRepo jobRepo;
    private final ColumnService columnService;
    private final EtlJourService jourService;
    private final VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;
    private final SystemConfigService configService;
    private final TargetService targetService;
    private final RiskLogService riskLogService;

    /**
     * 获取指定采集表的采集任务模板内容
     *
     * @param tid 采集表 ID
     * @return 采集任务模板内容（JSON字符串），若不存在则返回null
     */
    public String getJobContent(long tid)
    {
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
    public TaskResultDto updateJob(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return TaskResultDto.failure("没有指定采集任务", 0);
        }
        log.info("准备更新表 {}.{}({}) 的采集任务模板", etlTable.getTargetDb(), etlTable.getTargetTable(), etlTable.getId());
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.ADDAX_JOB, null);

        Map<String, String> values = new HashMap<>();
        values.put("reader", fillRdbmsReaderJob(etlTable));
        values.put("writer", fillHdfsWriterJob(etlTable));

        String jobTemplate = configService.getRdbms2HdfsJobTemplate();
        StringSubstitutor substitutor = new StringSubstitutor(values);
        String job = substitutor.replace(jobTemplate);

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
        jourService.successJour(etlJour);
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable());
        return TaskResultDto.success("更新采集任务模板成功", 0);
    }

    private String fillRdbmsReaderJob(VwEtlTableWithSource vTable)
    {
        String template = configService.getRdbmsReaderTemplate();

        Map<String, String> values = new HashMap<>();

        values.put("name", vTable.getDbType() + "reader");
        values.put("username", vTable.getUsername());
        values.put("password", vTable.getPass() == null ? "" : vTable.getPass());
        values.put("jdbcUrl", vTable.getUrl());
        if (vTable.getFilter().startsWith(SPECIAL_FILTER_PLACEHOLDER)) {
            // 需要解析过滤条件
            String parsedFilter = parseFilterCondition(vTable, vTable.getFilter());
            values.put("where", parsedFilter);
        }
        else {
            values.put("where", vTable.getFilter());
        }
        values.put("autoPk", String.valueOf(vTable.getAutoPk()));
        values.put("splitPk", vTable.getSplitPk() == null ? "" : vTable.getSplitPk());
        values.put("fetchSize", "20480");
        DbType dbType = getDbType(vTable.getUrl());
        if (dbType == DbType.POSTGRESQL) {
            values.put("table", quoteIfNeeded(vTable.getSourceTable(), dbType));
        }
        else {
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
            }
            else {
                // 如果列名是关键字，则还需要加上引号
                srcColumns.add("\"" + quoteIfNeeded(columnName, dbType) + "\"");
            }
        }
        values.put("column", String.join(", ", srcColumns));
        StringSubstitutor substitutor = new StringSubstitutor(values);
        return substitutor.replace(template);
    }

    private String fillHdfsWriterJob(VwEtlTableWithSource vTable)
    {

        Map<String, String> values = new HashMap<>();
        values.put("compress", vTable.getCompressFormat());
        values.put("fileType", vTable.getStorageFormat());

        // 处理列信息
        String columnJson = getHdfsWriteColumns(vTable);
        values.put("column", columnJson);

        // hdfs path 的前缀路径不应该在模板中填写，而应该从配置中获取
        Path hdfsPath = Paths.get(configService.getHdfsPrefix(), vTable.getTargetDb(), vTable.getTargetTable());

        if (!vTable.getPartName().isEmpty()) {
            String bizDate = configService.getBizDate();
            if (!Objects.equals(vTable.getPartFormat(), DEFAULT_PART_FORMAT)) {
                // 不是默认则 bizDate 日期格式，则需要进行转换
                bizDate = configService.getBizDateAsDate().format(DateTimeFormatter.ofPattern(vTable.getPartFormat()));
            }
            hdfsPath = hdfsPath.resolve(vTable.getPartName() + "=" + bizDate);
        }

        values.put("path", hdfsPath.toString());
        // 所有变量都需要处理，以防止模板替换错误

        String template = configService.getHdfsWriterTemplate();
        StringSubstitutor substitutor = new StringSubstitutor(values);

        return substitutor.replace(template);
    }

    private String getHdfsWriteColumns(VwEtlTableWithSource vTable)
    {
        List<EtlColumn> columnList = columnService.getColumns(vTable.getId());
        List<Map<String, String>> columns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
            Map<String, String> targetColumn = new HashMap<>();
            targetColumn.put("type", etlColumn.getTargetTypeFull());
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                // 目标表字段名还是正常的字段名
                targetColumn.put("name", columnName.substring(DELETED_PLACEHOLDER_PREFIX.length()));
            }
            else {
                targetColumn.put("name", columnName);
            }
            columns.add(targetColumn);
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(columns);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("column 转换为 JSON 失败", e);
        }
    }

    /**
     * 根据表 ID 删除对应的采集任务
     *
     * @param tableId 表ID
     */
    public void deleteByTid(long tableId)
    {
        jobRepo.deleteById(tableId);
    }

    /**
     * 解析特定过滤条件，并返回满足 Addax 要求的值
     * 如果过滤条件字符串是是以__max__开头，我们认定这是特定过滤条件
     * 他的格式为 __max__&lt;column_name&gt;
     * 代表需要取目标表中该列的最大值作为过滤条件
     * 这里的 &lt;column_name&gt; 我们要求比如整形数值类型，一般都是指向自增主键这样的字段
     */
    public String parseFilterCondition(VwEtlTableWithSource table, String filterCondition)
    {
        if (filterCondition.length() < 8 || !filterCondition.startsWith(SPECIAL_FILTER_PLACEHOLDER)) {
            return "1=1";
        }
        // 提取字段
        String columnName = filterCondition.substring(SPECIAL_FILTER_PLACEHOLDER.length());
        String partValue = configService.getL2TD();
        Long maxValue = targetService.getMaxValue(table, columnName, partValue);
        if (maxValue == null) {
            // 说明目标表还没有数据或者异常了，那么直接返回 1=1
            // 记录一条风险日志，提醒用户可能存在类型不兼容或查询异常
            try {
                String details = String.format("parseFilterCondition failed to get max value for tableId=%d, column=%s, part=%s", table.getId(), columnName, partValue);
                riskLogService.recordRisk("JobContentService", "WARN", "无法获取目标表最大值，使用默认过滤 1=1", details, table.getId());
            }
            catch (Exception ex) {
                log.warn("记录风险日志时出现异常: {}", ex.getMessage());
            }
            return "1=1";
        }
        return quoteIfNeeded(columnName, getDbType(table.getUrl())) + " > " + maxValue;
    }

    /**
     * 根据数据源 ID 异步更新相关的采集任务
     *
     * @param sid 数据源 ID
     */
    // 根据数据源 ID 更新相关的任务
    @Async
    public void updateJobBySourceId(int sid)
    {
        vwEtlTableWithSourceRepo.findBySidAndEnabledTrueAndStatusNot(sid, TableStatus.EXCLUDE_COLLECT)
            .forEach(this::updateJob);
    }

    @EventListener
    public void handleSourceUpdatedEvent(SourceUpdatedEvent event)
    {
        if (event.isConnectionChanged()) {
            updateJobBySourceId(event.getSourceId());
        }
    }
}
