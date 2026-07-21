package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


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
    private final StatService statService;
    private final ObjectMapper objectMapper;

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
     * Generate addax job JSON for a specific date, used by fillback.
     * Unlike {@link #getJobContent(long)} which returns a pre-generated template,
     * this method regenerates the template in-memory using the target date
     * for all date-dependent values (partition paths, WHERE conditions, dynamic table names).
     * The result is NOT persisted to etl_job table.
     *
     * @param tid        table ID
     * @param targetDate the fillback target date
     * @return generated job JSON, or null if table not found
     */
    public String getJobContentForDate(long tid, LocalDate targetDate)
    {
        VwEtlTableWithSource etlTable = vwEtlTableWithSourceRepo.findById(tid).orElse(null);
        if (etlTable == null) {
            log.warn("Table view not found for fillback template generation, tid={}", tid);
            return null;
        }

        Map<String, String> dateValues = configService.getBizDateValuesForDate(targetDate);
        // For incremental tables, use the day before target date as the "last etl date"
        LocalDate fillbackLastEtlDate = targetDate.minusDays(1);

        Map<String, String> values = new HashMap<>();
        values.put("reader", fillRdbmsReaderJobForDate(etlTable, dateValues, fillbackLastEtlDate));
        values.put("writer", targetService.buildWriterJobForDate(etlTable, targetDate));

        String jobTemplate = configService.getRdbms2HdfsJobTemplate();
        StringSubstitutor substitutor = new StringSubstitutor(values);
        return mergePluginConfigIntoJob(substitutor.replace(jobTemplate), etlTable);
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
        values.put("writer", targetService.buildWriterJob(etlTable));

        String jobTemplate = configService.getRdbms2HdfsJobTemplate();
        StringSubstitutor substitutor = new StringSubstitutor(values);
        String job = mergePluginConfigIntoJob(substitutor.replace(jobTemplate), etlTable);

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
        jourService.successJour(etlJour);
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable());
        return TaskResultDto.success("更新采集任务模板成功", 0);
    }

    private String mergePluginConfigIntoJob(String job, VwEtlTableWithSource etlTable)
    {
        JsonNode readerConfig = etlTable.getReaderPluginConfig();
        JsonNode writerConfig = etlTable.getWriterPluginConfig();
        if ((readerConfig == null || readerConfig.isNull()) && (writerConfig == null || writerConfig.isNull())) {
            return job;
        }
        try {
            JsonNode root = objectMapper.readTree(job);
            JsonNode contentNode = root.path("job").path("content");
            if (!(contentNode instanceof ObjectNode contentObject)) {
                log.warn("任务模板结构不符合预期，缺少 job.content 对象，tid={}", etlTable.getId());
                return job;
            }
            mergeConfigToParameter(contentObject, "reader", readerConfig, etlTable.getId());
            mergeConfigToParameter(contentObject, "writer", writerConfig, etlTable.getId());
            return objectMapper.writeValueAsString(root);
        }
        catch (Exception ex) {
            log.warn("合并插件配置失败，tid={}, err={}", etlTable.getId(), ex.getMessage());
            return job;
        }
    }

    private void mergeConfigToParameter(ObjectNode contentObject, String pluginName, JsonNode customConfig, Long tableId)
    {
        if (customConfig == null || customConfig.isNull()) {
            return;
        }
        if (!customConfig.isObject()) {
            log.warn("{} 插件配置必须是 JSON 对象，tid={}", pluginName, tableId);
            return;
        }

        JsonNode pluginNode = contentObject.get(pluginName);
        if (!(pluginNode instanceof ObjectNode pluginObject)) {
            log.warn("任务模板结构不符合预期，缺少 {} 对象，tid={}", pluginName, tableId);
            return;
        }

        JsonNode parameterNode = pluginObject.get("parameter");
        ObjectNode parameterObject;
        if (parameterNode == null || parameterNode.isNull()) {
            parameterObject = pluginObject.putObject("parameter");
        }
        else if (parameterNode instanceof ObjectNode objectNode) {
            parameterObject = objectNode;
        }
        else {
            log.warn("任务模板结构不符合预期，{}.parameter 不是对象，tid={}", pluginName, tableId);
            return;
        }

        customConfig.fields().forEachRemaining(entry -> parameterObject.set(entry.getKey(), entry.getValue()));
    }

    private String fillRdbmsReaderJob(VwEtlTableWithSource vTable)
    {
        return fillRdbmsReaderJobForDate(vTable, configService.getBizDateValues(), null);
    }

    /**
     * Build reader JSON with custom date values and optional lastEtlDate override.
     * Used by fillback to generate templates for a specific target date.
     *
     * @param vTable           table view
     * @param dateValues       pre-computed date variable map
     * @param overrideLastEtlDate if non-null, used as the "last etl date" for incremental filters
     *                            instead of querying etl_statistic
     * @return reader JSON fragment
     */
    private String fillRdbmsReaderJobForDate(VwEtlTableWithSource vTable, Map<String, String> dateValues, LocalDate overrideLastEtlDate)
    {
        String template = configService.getRdbmsReaderTemplate();

        Map<String, String> values = new HashMap<>();

        values.put("name", vTable.getDbType() + "reader");
        values.put("username", vTable.getUsername());
        values.put("password", vTable.getPass() == null ? "" : vTable.getPass());
        values.put("jdbcUrl", vTable.getUrl());
        if (vTable.getFilter().startsWith(SPECIAL_FILTER_PLACEHOLDER)) {
            // incremental filter: use override date if provided, otherwise query latest stat
            LocalDate lastEtlDate = overrideLastEtlDate != null
                ? overrideLastEtlDate
                : statService.getLastEtlDateByTid(vTable.getId());
            if (lastEtlDate != null) {
                String parsedFilter = parseFilterCondition(vTable, vTable.getFilter(), lastEtlDate);
                values.put("where", parsedFilter);
            } else {
                values.put("where", "1=1");
            }
        }
        else {
            values.put("where", vTable.getFilter());
        }
        values.put("autoPk", String.valueOf(vTable.getAutoPk()));
        values.put("splitPk", vTable.getSplitPk() == null ? "" : vTable.getSplitPk());
        values.put("fetchSize", "20480");

        // date time special values
        values.putAll(dateValues);

        DbType dbType = getDbType(vTable.getUrl());
        String sourceTable = vTable.getSourceTable();
        if (sourceTable.contains("${")) {
            // dynamic table name: replace date placeholders
            StringSubstitutor stringSubstitutor = new StringSubstitutor(dateValues);
            sourceTable = stringSubstitutor.replace(sourceTable);
        }
        if (dbType == DbType.POSTGRESQL) {
            values.put("table", quoteIfNeeded(sourceTable, dbType));
        }
        else {
            values.put("table", quoteIfNeeded(vTable.getSourceDb(), dbType) + "." + quoteIfNeeded(sourceTable, dbType));
        }

        // process column info
        List<EtlColumn> columnList = columnService.getColumns(vTable.getId());
        List<String> srcColumns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                srcColumns.add("\"NULL\"");
            }
            else {
                srcColumns.add("\"" + quoteIfNeeded(columnName, dbType) + "\"");
            }
        }
        values.put("column", String.join(", ", srcColumns));
        StringSubstitutor substitutor = new StringSubstitutor(values);
        return substitutor.replace(template);
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
     * @param table         采集表视图对象
     * @param filterCondition 过滤条件字符串
     * @param lastEtlDate  上次采集的业务日期(yyyy-MM-dd)
     *
     * @return 解析后的过滤条件字符串, 如果无法解析则返回 "1=1"
     */
    public String parseFilterCondition(VwEtlTableWithSource table, String filterCondition, LocalDate lastEtlDate)
    {
        if (filterCondition.length() < 8 || !filterCondition.startsWith(SPECIAL_FILTER_PLACEHOLDER)) {
            return "1=1";
        }
        // 提取字段
        String columnName = filterCondition.substring(SPECIAL_FILTER_PLACEHOLDER.length());
        // 分区的日期格式
        String partFormat = table.getPartFormat();
        // 转为指定格式
        String partValue = lastEtlDate.format(DateTimeFormatter.ofPattern(partFormat));
        Object maxValue = targetService.getMaxValue(table, columnName, partValue);
        if (maxValue == null) {
            // 说明目标表还没有数据或者异常了，那么直接返回 1=1
            // 记录一条风险日志，提醒用户可能存在类型不兼容或查询异常
            try {
                riskLogService.recordRisk("JobContentService", "WARN", "无法获取目标表(" +  table.getTargetDb() + "." + table.getTargetTable() + ")最大值，使用默认过滤 1=1", table.getId());
            }
            catch (Exception ex) {
                log.warn("记录风险日志时出现异常: {}", ex.getMessage());
            }
            return "1=1";
        }
        try {
            Long.parseLong(maxValue.toString());
            return quoteIfNeeded(columnName, getDbType(table.getUrl())) + " > " + maxValue;
        }
        catch (NumberFormatException e) {
            return quoteIfNeeded(columnName, getDbType(table.getUrl())) + " > '" + maxValue + "'";
        }
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

    public void updateJobContent(long tableId, String jobContent) {
        EtlJob etlJob = jobRepo.findById(tableId).orElse(new EtlJob());
        etlJob.setJob(jobContent);
        jobRepo.save(etlJob);
    }
}
