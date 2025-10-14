package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX
import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.model.EtlColumn
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.repository.EtlColumnRepo
import com.wgzhao.addax.admin.utils.DbUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.BeanUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSetMetaData
import java.sql.SQLException

/**
 * 采集表字段信息服务类，负责采集表字段的增删改查及同步等业务操作
 */
@Service
open class ColumnService(
    private val etlColumnRepo: EtlColumnRepo,
    private val dictService: DictService,
    private val jourService: EtlJourService
) {
    private val log = KotlinLogging.logger {}

    /**
     * 获取指定采集表的所有字段信息
     * @param tid 采集表ID
     * @return 字段列表
     */
    fun getColumns(tid: Long): List<EtlColumn> =
        etlColumnRepo.findAllByTidOrderByColumnId(tid).filterNotNull()

    /**
     * 更新当前表的字段信息，主要涉及到源表字段的变更
     * 逻辑：
     * 1. 新增字段一定是追加在最后
     * 2. 删除字段则将源字段名设置为 __deleted__ 前缀
     * 3. 字段类型变更则同步更新并记录到风险表
     * 4. 新增字段直接插入
     * @param etlTable 采集表视图对象
     * @return 0 表示无需更新, 1 表示字段有更新, -1 表示更新失败
     */
    @Transactional
    open fun updateTableColumns(etlTable: VwEtlTableWithSource?): Int {
        if (etlTable == null) return 0
        val existingColumns = etlColumnRepo.findAllByTidOrderByColumnId(etlTable.id).filterNotNull().toMutableList()
        if (existingColumns.isEmpty()) {
            return if (createTableColumns(etlTable)) 1 else -1
        }
        log.info { "updating table columns for tid ${etlTable.sourceDb}.${etlTable.sourceTable} (${etlTable.id})" }
        val etlJour = jourService.addJour(etlTable.id, JourKind.UPDATE_COLUMN, null)
        val hiveTypeMapping = dictService.getHiveTypeMapping()
        val sql = "select * from `${etlTable.sourceDb}`.`${etlTable.sourceTable}` where 1=0"
        var changed = false
        try {
            DriverManager.getConnection(etlTable.url, etlTable.username, etlTable.pass).use { connection ->
                connection.createStatement().executeQuery(sql).use { rs ->
                    val md = rs.metaData
                    val n = md.columnCount
                    val sourceCols = mutableListOf<EtlColumn>()
                    for (i in 1..n) {
                        sourceCols.add(getEtlColumn(etlTable, i, md, connection, hiveTypeMapping))
                    }
                    var o = 0
                    var s = 0
                    val m = existingColumns.size
                    while (o < m && s < n) {
                        val oc = existingColumns[o]
                        if (isDeletedPlaceholder(oc.columnName)) {
                            o++
                            continue
                        }
                        val sc = sourceCols[s]
                        if (oc.columnName == sc.columnName) {
                            val typeChanged = (oc.sourceType != sc.sourceType) || notEq(oc.dataPrecision, sc.dataPrecision) || notEq(oc.dataScale, sc.dataScale) || oc.dataLength != sc.dataLength
                            if (typeChanged) {
                                log.warn {
                                    "RISK[COLUMN_TYPE_CHANGE] tid=${etlTable.id}, table=${etlTable.sourceDb}.${etlTable.sourceTable}, column=${sc.columnName}, srcType: ${oc.sourceType}(len=${oc.dataLength},p=${nvl(oc.dataPrecision)},s=${nvl(oc.dataScale)}) -> ${sc.sourceType}(len=${sc.dataLength},p=${nvl(sc.dataPrecision)},s=${nvl(sc.dataScale)})"
                                }
                                oc.setSourceType(sc.getSourceType())
                                oc.setDataLength(sc.getDataLength())
                                oc.setDataPrecision(sc.getDataPrecision())
                                oc.setDataScale(sc.getDataScale())
                                oc.setColComment(sc.getColComment())
                                oc.setTargetType(sc.getTargetType())
                                oc.setTargetTypeFull(sc.getTargetTypeFull())
                                etlColumnRepo.save(oc)
                                changed = true
                            }
                            o++
                            s++
                        } else {
                            val placeholder = DELETED_PLACEHOLDER_PREFIX + oc.getColumnName()
                            oc.setColumnName(placeholder)
                            etlColumnRepo.save(oc)
                            changed = true
                            o++
                        }
                    }
                    while (o < m) {
                        val oc = existingColumns[o++]
                        if (!isDeletedPlaceholder(oc.getColumnName())) {
                            val placeholder = DELETED_PLACEHOLDER_PREFIX + oc.getColumnName()
                            oc.setColumnName(placeholder)
                            etlColumnRepo.save(oc)
                            changed = true
                        }
                    }
                    var nextId = m
                    while (s < n) {
                        val sc = sourceCols[s++]
                        val nc = EtlColumn()
                        BeanUtils.copyProperties(sc, nc)
                        nc.setColumnId(++nextId)
                        etlColumnRepo.save(nc)
                        changed = true
                    }
                }
            }
        } catch (e: SQLException) {
            jourService.failJour(etlJour, e.message)
            log.error(e) { "failed to update table columns for tid ${etlTable.getId()}" }
            return -1
        }
        log.info { "table columns updated for tid ${etlTable.getId()}, changed=$changed" }
        jourService.successJour(etlJour)
        return if (changed) 1 else 0
    }

    /**
     * 当新增采集表时，添加表的字段信息到 etl_column 表，他包含了源表的字段信息和目标表的字段信息
     * @param etlTable etl_table 表记录
     * @return true 成功，false 失败
     */
    @Transactional
    fun createTableColumns(etlTable: VwEtlTableWithSource?): Boolean {
        if (etlTable == null) return false
        log.info { "first add table columns for tid ${etlTable.getSourceDb()}.${etlTable.getSourceTable()} (${etlTable.getId()})" }
        val etlJour = jourService.addJour(etlTable.getId(), JourKind.CREATE_COLUMN, null)
        val hiveTypeMapping = dictService.getHiveTypeMapping()
        val sql = "select * from `${etlTable.getSourceDb()}`.`${etlTable.getSourceTable()}` where 1=0"
        try {
            DriverManager.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass()).use { connection ->
                connection.createStatement().executeQuery(sql).use { resultSet ->
                    val metaData = resultSet.metaData
                    val columnCount = metaData.columnCount
                    for (i in 1..columnCount) {
                        val etlColumn = getEtlColumn(etlTable, i, metaData, connection, hiveTypeMapping)
                        etlColumnRepo.save(etlColumn)
                    }
                    log.info { "table columns created for tid ${etlTable.getId()}, total $columnCount columns" }
                    jourService.successJour(etlJour)
                    return true
                }
            }
        } catch (e: SQLException) {
            jourService.failJour(etlJour, e.message)
            log.error(e) { "failed to create table columns for tid ${etlTable.getId()}" }
            return false
        }
    }

    /**
     * 获取指定采集表的Hive列信息并转换为DDL语句
     * @param tid 采集表ID
     * @return DDL语句列表
     */
    fun getHiveColumnsAsDDL(tid: Long): List<String> {
        val result = mutableListOf<String>()
        val columns = getColumns(tid)
        for (col in columns) {
            val colName = if (isDeletedPlaceholder(col.getColumnName())) {
                col.getColumnName().substring(DELETED_PLACEHOLDER_PREFIX.length)
            } else {
                col.getColumnName()
            }
            if (col.getColComment().isEmpty()) {
                result.add("$colName ${col.getTargetTypeFull()}")
            } else {
                result.add("$colName ${col.getTargetTypeFull()} COMMENT '${nvlStr(col.getColComment())}'")
            }
        }
        return result
    }

    /**
     * 根据表ID删除对应的字段信息
     * @param tableId 表ID
     */
    fun deleteByTid(tableId: Long) {
        etlColumnRepo.deleteAllByTid(tableId)
    }

    companion object {
        private fun isDeletedPlaceholder(name: String?): Boolean =
            name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX)

        private fun notEq(a: Int?, b: Int?): Boolean = a != b

        private fun nvl(v: Int?): Int = v ?: 0

        private fun nvlStr(v: String?): String = v ?: ""

        @Throws(SQLException::class)
        private fun getEtlColumn(
            etlTable: VwEtlTableWithSource,
            i: Int,
            metaData: ResultSetMetaData,
            connection: Connection,
            hiveTypeMapping: Map<String?, String?>
        ): EtlColumn {
            val etlColumn = EtlColumn()
            etlColumn.setTid(etlTable.getId())
            etlColumn.setColumnId(i)
            etlColumn.setColumnName(metaData.getColumnName(i))
            etlColumn.setSourceType(metaData.getColumnTypeName(i))
            etlColumn.setDataLength(metaData.getColumnDisplaySize(i))
            etlColumn.setDataPrecision(metaData.getPrecision(i))
            etlColumn.setDataScale(metaData.getScale(i))
            val colComment = DbUtil.getColumnComment(connection, etlTable.getSourceDb(), etlTable.getSourceTable(), metaData.getColumnName(i))
            etlColumn.setColComment(colComment)
            var hiveType = hiveTypeMapping.getOrDefault(metaData.getColumnTypeName(i), "string")
            etlColumn.setTargetType(hiveType)
            if (hiveType == "decimal") {
                hiveType = "decimal(${metaData.getPrecision(i)},${metaData.getScale(i)})"
            }
            etlColumn.setTargetTypeFull(hiveType)
            return etlColumn
        }
    }
}
