package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJob;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

@Service
@Slf4j
public class JobContentService
{
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

    public String getJobContent(long tid)
    {
        return jobRepo.findById(tid).map(EtlJob::getJob).orElse(null);
    }

    /**
     * 更新采集任务的 json 文件表
     * 他扫描 tb_imp_etl 任务，然后生成 addax 采集需要的 json 文件模板，并写入到 tb_imp_etl_job 表中
     * 这个方法可以定期运行，确保 tb_imp_etl_job 表中的 json
     */
    public TaskResultDto updateJob(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null) {
            return  TaskResultDto.failure( "没有指定采集任务", 0);
        }
        log.info("准备更新表 {}.{}({}) 的采集任务模板", etlTable.getTargetDb(), etlTable.getTargetTable(), etlTable.getId());
        EtlJour etlJour =  jourService.addJour(etlTable.getId(), JourKind.ADDAX_JOB, null);
        // 这里对源 DB 和 TABLE 做了 quote，用于处理不规范命名的问题，比如 mysql 中的关键字作为表名等 ，库名包含中划线(-)
        // TODO 这里直接使用 ` 来做 quote，可能不适用于所有数据库，比如 Oracle 需要使用 " 来做 quote
        // 需要根据不同数据库类型做不同的处理
        String kind = DbUtil.getKind(etlTable.getUrl());
        String addaxReaderContentTemplate = dictService.getReaderTemplate(kind);
//        String columns = columnService.getSourceColumns(etlTable.getId());

        Map<String, String> params = new HashMap<>();
        params.put("url", etlTable.getUrl());
        params.put("username", etlTable.getUsername());
        params.put("pass", etlTable.getPass());
        params.put("filter", etlTable.getFilter());
        params.put("table_name", "`" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "`");
        List<EtlColumn> columnList = columnService.getColumns(etlTable.getId());
        List<String> srcColumns = new ArrayList<>();
        // hdfswrite 中的 column 项需要使用 {"name": "<column name>":,"type":"<data type>"} 的格式
        String typeTemplate = """
                {"name": "%s", "type":"%s"}
                """;
        List<String> destColumns = new ArrayList<>();
        for (EtlColumn etlColumn : columnList) {
            String columnName = etlColumn.getColumnName();
            String targetColumn = typeTemplate.formatted(columnName, etlColumn.getTargetTypeFull());
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                // 被标记为删除的字段，那么使用 null 来填充该字段
                srcColumns.add("\"NULL\"");
                // 目标表字段名还是正常的字段名
                targetColumn = typeTemplate.formatted(columnName.substring(DELETED_PLACEHOLDER_PREFIX.length()), etlColumn.getTargetTypeFull());
            }
            else {
                srcColumns.add("\"" +columnName + "\"");
            }
            destColumns.add(targetColumn);
        }
        params.put("column", String.join(",", srcColumns));
        addaxReaderContentTemplate = replacePlaceholders(addaxReaderContentTemplate, params);

        String addaxWriterTemplate = dictService.getItemValue(5001, "wH", String.class);
        // col_idx = 1000 的字段为分区字段，不参与 select
        //TODO 路径结合应该考虑尾部 / 的问题
        String hdfsPath = configService.getHDFSPrefix() + "/" + etlTable.getTargetDb() + "/" + etlTable.getTargetTable();
        if (!etlTable.getPartName().isEmpty()) {
            hdfsPath += "/" + etlTable.getPartName() + "=${logdate}";
        }
        params.put("hdfs_path", hdfsPath);
        params.put("column", String.join(",", destColumns));
        addaxWriterTemplate = replacePlaceholders(addaxWriterTemplate, params);
        String job = dictService.getAddaxJobTemplate(kind + "2H");
        if (job == null || job.isEmpty()) {
            String msg = "没有获得 " + kind + "2H 的预设模板，检查系统配置表";
            jourService.failJour(etlJour, msg);
            log.warn(msg);
            return TaskResultDto.failure(msg, 0);
        }
        job = job.replace("${r" + kind + "}", addaxReaderContentTemplate).replace("${wH}", addaxWriterTemplate);

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
        jourService.successJour(etlJour);
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable());
        return TaskResultDto.success("更新采集任务模板成功", 0);
    }

    private String replacePlaceholders(String template, Map<String, String> values)
    {
        if (StringUtils.isBlank(template) || values.isEmpty()) {
            return template;
        }

        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key);
            // 当值为 null 时，使用空字符串 '' 替代
            String replacement = value != null ? value.toString() : "";
//            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public void deleteByTid(long tableId)
    {
        jobRepo.deleteById(tableId);
    }
}
