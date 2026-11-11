package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.event.SourceUpdatedEvent;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlJob;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.wgzhao.addax.admin.service.source.ReaderJobContext;
import com.wgzhao.addax.admin.service.source.SourceAdapter;
import com.wgzhao.addax.admin.service.source.SourceAdapterRegistry;
import com.wgzhao.addax.admin.common.TargetFamily;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


/**
 * 采集任务内容服务类，负责采集任务的模板生成与更新等相关操作
 */
@Service
@Slf4j
@AllArgsConstructor
public class JobContentService
{

    /** Placeholders left after generation, used to surface unknown/misspelled business variables. */
    private static final Pattern BIZ_VARIABLE_PATTERN = Pattern.compile("\\$\\{(biz_\\w+)}");

    private final EtlJobRepo jobRepo;
    private final EtlTableRepo tableRepo;
    private final ColumnService columnService;
    private final ColumnMappingService columnMappingService;
    private final EtlJourService jourService;
    private final VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;
    private final SystemConfigService configService;
    private final TargetAdapterRegistry targetAdapterRegistry;
    private final StatService statService;
    private final ObjectMapper objectMapper;
    private final CalendarService calendarService;
    private final SourceAdapterRegistry sourceAdapterRegistry;

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
        // For incremental tables, use the target date's previous business day as the "last etl date".
        // Walking back one calendar day would pick a rest day for workday/trading-day sources, so the
        // window would open earlier than the data that actually exists.
        LocalDate fillbackLastEtlDate = calendarService.previousBusinessDay(etlTable.getCalendar(), targetDate);

        Map<String, String> values = new HashMap<>();
        values.put("reader", buildReaderJob(etlTable, dateValues, fillbackLastEtlDate));
        values.put("writer", targetAdapterRegistry.resolve(etlTable).buildWriterJobForDate(etlTable, targetDate));

        String jobTemplate = resolveJobTemplate(etlTable);
        StringSubstitutor substitutor = new StringSubstitutor(values);
        String job = mergePluginConfigIntoJob(substitutor.replace(jobTemplate), etlTable);
        return substituteBizDates(job, dateValues, tid);
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

        Map<String, String> dateValues = configService.getBizDateValues();
        Map<String, String> values = new HashMap<>();
        values.put("reader", buildReaderJob(etlTable, dateValues, null));
        values.put("writer", targetAdapterRegistry.resolve(etlTable).buildWriterJob(etlTable));

        String jobTemplate = resolveJobTemplate(etlTable);
        StringSubstitutor substitutor = new StringSubstitutor(values);
        String job = mergePluginConfigIntoJob(substitutor.replace(jobTemplate), etlTable);
        job = substituteBizDates(job, dateValues, etlTable.getId());

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
        jourService.successJour(etlJour);
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable());
        return TaskResultDto.success("更新采集任务模板成功", 0);
    }

    /**
     * Pick the main job template by target family. The R2H and R2R templates are structurally identical;
     * keeping them separate lets RDBMS targets tune their own speed/setting without touching HDFS jobs.
     * The family comes from the same resolver the adapter registry uses, so the template and adapter
     * never disagree (e.g. writer key wR with a stale target_type).
     */
    private String resolveJobTemplate(VwEtlTableWithSource etlTable)
    {
        TargetFamily family = targetAdapterRegistry.resolve(etlTable).getFamily();
        if (family == TargetFamily.HDFS)
        {
            return configService.getRdbms2HdfsJobTemplate();
        }
        return configService.getRdbms2RdbmsJobTemplate();
    }

    /**
     * The reader fragment is already final when this runs: the source adapter merged its per-table
     * reader config (MongoDB even combines a custom query with the generated one). Merging it again
     * here would overwrite that combination, so only the writer is merged.
     */
    private String mergePluginConfigIntoJob(String job, VwEtlTableWithSource etlTable)
    {
        JsonNode writerConfig = etlTable.getWriterPluginConfig();
        if (writerConfig == null || writerConfig.isNull()) {
            return job;
        }
        try {
            JsonNode root = objectMapper.readTree(job);
            JsonNode contentNode = root.path("job").path("content");
            if (!(contentNode instanceof ObjectNode contentObject)) {
                log.warn("任务模板结构不符合预期，缺少 job.content 对象，tid={}", etlTable.getId());
                return job;
            }
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

    /**
     * Substitute business-date variables in the final job, including plugin configs merged after
     * the template was rendered.
     *
     * <p>Only {@code biz_*} is replaced here: {@code curr_*} must stay unresolved so the addax
     * process resolves it to the real run time via its {@code -p} properties. This is also why the
     * map is filtered instead of reusing the full date map.
     *
     * @param job        final job JSON
     * @param dateValues variables of the run's business date
     * @param tid        table id, for the warning
     * @return job with business dates resolved
     */
    private String substituteBizDates(String job, Map<String, String> dateValues, long tid)
    {
        if (job == null || job.isEmpty() || dateValues == null || dateValues.isEmpty()) {
            return job;
        }
        Map<String, String> bizValues = new HashMap<>();
        dateValues.forEach((name, value) -> {
            if (name.startsWith("biz_")) {
                bizValues.put(name, value);
            }
        });

        String substituted = new StringSubstitutor(bizValues).replace(job);
        Matcher matcher = BIZ_VARIABLE_PATTERN.matcher(substituted);
        Set<String> unresolved = new TreeSet<>();
        while (matcher.find()) {
            unresolved.add(matcher.group(1));
        }
        if (!unresolved.isEmpty()) {
            log.warn("Job for tid={} still contains unresolved biz_ variables: {}", tid, unresolved);
        }
        return substituted;
    }

    /**
     * Resolve the engine-agnostic parts of the reader (last business date and target MAX lookup) and
     * let the source adapter render the fragment. The adapter owns the filter syntax and the reader
     * shape; the common layer only knows that a target may have a max value.
     */
    private String buildReaderJob(VwEtlTableWithSource table, Map<String, String> dateValues,
        LocalDate overrideLastEtlDate)
    {
        SourceAdapter adapter = sourceAdapterRegistry.resolve(table);
        TargetAdapter targetAdapter = targetAdapterRegistry.resolve(table);
        List<ColumnMappingService.ResolvedColumnMapping> mappings = columnMappingService.resolve(table.getId());
        String filter = table.getFilter() == null ? "" : table.getFilter();

        LocalDate lastEtlDate = null;
        String partValue = null;
        if (adapter.isIncrementalFilter(filter))
        {
            lastEtlDate = overrideLastEtlDate != null
                ? overrideLastEtlDate
                : statService.getLastEtlDateByTid(table.getId());
            if (lastEtlDate != null)
            {
                // The RDBMS snapshot column holds biz_date_short (yyyyMMdd). A stale HDFS
                // partFormat (or the reverse) must not change how the max lookup scopes that date.
                boolean rdbmsTarget = targetAdapter.getFamily() == TargetFamily.RDBMS;
                String partFormat = rdbmsTarget ? "yyyyMMdd" : table.getPartFormat();
                if (partFormat == null || partFormat.isBlank())
                {
                    partFormat = "yyyyMMdd";
                }
                partValue = lastEtlDate.format(DateTimeFormatter.ofPattern(partFormat));
            }
        }

        ReaderJobContext context = new ReaderJobContext(dateValues, lastEtlDate, partValue, mappings,
            (column, part) -> targetAdapter.getMaxValue(table, column, part));
        ObjectNode reader = adapter.buildReaderJob(table, context);
        if (reader.get("parameter") instanceof ObjectNode parameter)
        {
            adapter.mergeReaderPluginConfig(parameter, table.getReaderPluginConfig(), table, dateValues);
        }
        return reader.toString();
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
     * 根据数据源 ID 异步更新相关的采集任务
     *
     * @param sid 数据源 ID
     */
    // 根据数据源 ID 更新相关的任务
    @Async
    public void updateJobBySourceId(int sid)
    {
        vwEtlTableWithSourceRepo.findBySidAndEnabledTrueAndStatusNotAndRetiredAtIsNull(
                sid, TableStatus.EXCLUDE_COLLECT)
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
        EtlTable table = tableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        if (table.getRetiredAt() != null) {
            throw new ApiException(409, "采集表已退役，恢复后才能修改任务");
        }
        EtlJob etlJob = jobRepo.findById(tableId).orElseGet(() -> {
            EtlJob newJob = new EtlJob();
            newJob.setTid(tableId);
            return newJob;
        });
        etlJob.setJob(jobContent);
        jobRepo.save(etlJob);
    }
}
