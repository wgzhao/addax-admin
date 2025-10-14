package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.common.TableStatus
import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.success
import com.wgzhao.addax.admin.event.SourceUpdatedEvent
import com.wgzhao.addax.admin.model.EtlJob
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.repository.EtlJobRepo
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo
import com.wgzhao.addax.admin.utils.DbUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.StringUtils
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.Any
import kotlin.Int
import kotlin.Long
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.isEmpty
import kotlin.text.StringBuilder
import kotlin.text.isEmpty
import kotlin.text.replace
import kotlin.text.trimIndent

/**
 * 采集任务内容服务类，负责采集任务的模板生成与更新等相关操作
 */
@Service
open class JobContentService(
    private val jobRepo: EtlJobRepo,
    private val dictService: DictService,
    private val columnService: ColumnService,
    private val configService: SystemConfigService,
    private val jourService: EtlJourService,
    private val vwEtlTableWithSourceRepo: VwEtlTableWithSourceRepo
) {

    private val log = KotlinLogging.logger {}
    /**
     * 获取指定采集表的采集任务模板内容
     * @param tid 采集表ID
     * @return 采集任务模板内容（JSON字符串），若不存在则返回null
     */
    fun getJobContent(tid: Long): String? {
        return jobRepo.findById(tid).orElse(null)?.job
    }

    /**
     * 更新采集任务的json模板
     * 扫描tb_imp_etl任务，生成addax采集需要的json模板，并写入tb_imp_etl_job表
     * 可定期运行，确保tb_imp_etl_job表中的json内容最新
     * @param etlTable 采集表视图对象
     * @return 任务结果
     */
    fun updateJob(etlTable: VwEtlTableWithSource?): TaskResultDto {
        if (etlTable == null) {
            return failure("没有指定采集任务", 0)
        }
        log.info {"准备更新表 ${etlTable.targetDb}.${etlTable.targetTable}({$etlTable.id}) 的采集任务模板" }
        val etlJour = jourService.addJour(etlTable.id, JourKind.ADDAX_JOB, null)
        // 这里对源 DB 和 TABLE 做了 quote，用于处理不规范命名的问题，比如 mysql 中的关键字作为表名等 ，库名包含中划线(-)
        // TODO 这里直接使用 ` 来做 quote，可能不适用于所有数据库，比如 Oracle 需要使用 " 来做 quote
        // 需要根据不同数据库类型做不同的处理
        val kind = DbUtil.getKind(etlTable.url)
        var addaxReaderContentTemplate = dictService.getReaderTemplate(kind)

        //        String columns = columnService.getSourceColumns(etlTable.getId());
        val params: MutableMap<String?, String?> = HashMap<String?, String?>()
        params.put("url", etlTable.getUrl())
        params.put("username", etlTable.getUsername())
        params.put("pass", etlTable.getPass())
        params.put("filter", etlTable.getFilter())
        params.put("table_name", "`" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "`")
        val columnList = columnService!!.getColumns(etlTable.getId())
        val srcColumns: MutableList<String?> = ArrayList<String?>()
        // hdfswrite 中的 column 项需要使用 {"name": "<column name>":,"type":"<data type>"} 的格式
        val typeTemplate = """
                {"name": "%s", "type":"%s"}
                
                """.trimIndent()
        val destColumns: MutableList<String?> = ArrayList<String?>()
        for (etlColumn in columnList) {
            val columnName: String = etlColumn.getColumnName()
            var targetColumn: String = typeTemplate.formatted(columnName, etlColumn.getTargetTypeFull())
            if (columnName.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                // 被标记为删除的字段，那么使用 null 来填充该字段
                srcColumns.add("\"NULL\"")
                // 目标表字段名还是正常的字段名
                targetColumn = typeTemplate.formatted(columnName.substring(DELETED_PLACEHOLDER_PREFIX.length), etlColumn.getTargetTypeFull())
            } else {
                srcColumns.add("\"" + columnName + "\"")
            }
            destColumns.add(targetColumn)
        }
        params.put("column", String.join(",", srcColumns))
        addaxReaderContentTemplate = replacePlaceholders(addaxReaderContentTemplate, params)

        var addaxWriterTemplate = dictService.getItemValue<kotlin.String>(5001, "wH", kotlin.String::class.java)
        // col_idx = 1000 的字段为分区字段，不参与 select
        //TODO 路径结合应该考虑尾部 / 的问题
        var hdfsPath = configService!!.getHDFSPrefix() + "/" + etlTable.getTargetDb() + "/" + etlTable.getTargetTable()
        if (!etlTable.getPartName().isEmpty()) {
            hdfsPath += "/" + etlTable.getPartName() + "=\${logdate}"
        }
        params.put("hdfs_path", hdfsPath)
        params.put("column", String.join(",", destColumns))
        addaxWriterTemplate = replacePlaceholders(addaxWriterTemplate, params)
        var job = dictService.getAddaxJobTemplate(kind + "2H")
        if (job == null || job.isEmpty()) {
            val msg = "没有获得 " + kind + "2H 的预设模板，检查系统配置表"
            jourService.failJour(etlJour, msg)
            JobContentService.log.warn(msg)
            return failure(msg, 0)
        }
        job = job.replace("\${r" + kind + "}", addaxReaderContentTemplate).replace("\${wH}", addaxWriterTemplate)

        val etlJob = EtlJob(etlTable.getId(), job)
        jobRepo!!.save<EtlJob?>(etlJob)
        jourService.successJour(etlJour)
        log.info("表 {}.{} 更新完成", etlTable.getTargetDb(), etlTable.getTargetTable())
        return success("更新采集任务模板成功", 0)
    }

    private fun replacePlaceholders(template: kotlin.String, values: MutableMap<kotlin.String?, kotlin.String?>): kotlin.String {
        if (StringUtils.isBlank(template) || values.isEmpty()) {
            return template
        }

        val pattern = Pattern.compile("\\$\\{([^}]+)\\}")
        val matcher = pattern.matcher(template)
        val result = StringBuilder()

        while (matcher.find()) {
            val key = matcher.group(1)
            val value: Any? = values.get(key)
            // 当值为 null 时，使用空字符串 '' 替代
            val replacement = value?.toString() ?: ""
            //            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)

        return result.toString()
    }

    /**
     * 根据表ID删除对应的采集任务
     * @param tableId 表ID
     */
    fun deleteByTid(tableId: Long) {
        jobRepo.deleteById(tableId)
    }

    /**
     * 根据数据源ID异步更新相关的采集任务
     * @param sid 数据源ID
     */
    // 根据数据源 ID 更新相关的任务
    @Async
    open fun updateJobBySourceId(sid: Int) {
        vwEtlTableWithSourceRepo.findBySidAndEnabledTrueAndStatusNot(sid, TableStatus.EXCLUDE_COLLECT)!!
            .forEach(Consumer { etlTable: VwEtlTableWithSource? -> this.updateJob(etlTable) })
    }

    @EventListener
    fun handleSourceUpdatedEvent(event: SourceUpdatedEvent) {
        if (event.connectionChanged) {
            updateJobBySourceId(event.sourceId)
        }
    }
}
