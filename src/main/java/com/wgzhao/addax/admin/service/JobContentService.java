package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlJob;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.repository.EtlJobRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
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

    public String getJobContent(long tid)
    {
        return jobRepo.findById(tid).map(EtlJob::getJob).orElse(null);
    }

    /**
     * 更新采集任务的 json 文件表
     * 他扫描 tb_imp_etl 任务，然后生成 addax 采集需要的 json 文件模板，并写入到 tb_imp_etl_job 表中
     * 这个方法可以定期运行，确保 tb_imp_etl_job 表中的 json
     */
    public void updateJob(EtlTable etlTable)
    {
        if (etlTable == null) {
            return;
        }

        // 这里对源 DB 和 TABLE 做了 quote，用于处理不规范命名的问题，比如 mysql 中的关键字作为表名等 ，库名包含中划线(-)
        // TODO 这里直接使用 ` 来做 quote，可能不适用于所有数据库，比如 Oracle 需要使用 " 来做 quote
        // 需要根据不同数据库类型做不同的处理
        EtlSource etlSource = etlTable.getEtlSource();
        String kind = DbUtil.getKind(etlSource.getUrl());
        String addaxReaderContentTemplate = dictService.getReaderTemplate(kind);
        String columns = columnService.getSourceColumns(etlTable.getId());
        Map<String, String> params = new HashMap<>();
        params.put("url", etlSource.getUrl());
        params.put("username", etlSource.getUsername());
        params.put("pass", etlSource.getPass());
        params.put("filter", etlTable.getFilter());
        params.put("table_name", "`" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "`");
        params.put("columns", columns);

        String addaxWriterTemplate = dictService.getItemValue(5001, "wH", String.class);
        // col_idx = 1000 的字段为分区字段，不参与 select
        //TODO ods, logdate 这些应该从配置获取
        String hdfsPath = configService.getHDFSPrefix() + etlTable.getTargetDb() + "/" + etlTable.getTargetTable();
        if (!etlTable.getPartName().isEmpty()) {
            hdfsPath += "/" + etlTable.getPartName() + "=${logdate}";
        }
        params.put("hdfs_path", hdfsPath);
        addaxReaderContentTemplate = replacePlaceholders(addaxReaderContentTemplate, params);
        addaxWriterTemplate = replacePlaceholders(addaxWriterTemplate, params);
        String job = dictService.getAddaxJobTemplate(kind + "2H");
        if (job == null || job.isEmpty()) {
            return;
        }
        job = job.replace("${r" + kind + "}", addaxReaderContentTemplate).replace("${wH}", addaxWriterTemplate);

        EtlJob etlJob = new EtlJob(etlTable.getId(), job);
        jobRepo.save(etlJob);
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
}
