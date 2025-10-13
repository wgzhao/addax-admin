package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.model.EtlColumn
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.repository.EtlColumnRepo
import com.wgzhao.addax.admin.utils.DbUtil
import lombok.AllArgsConstructor
import lombok.extern.slf4j.Slf4j
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
@Slf4j
@AllArgsConstructor
class ColumnService {
    private val etlColumnRepo: EtlColumnRepo? = null
    private val dictService: DictService? = null
    private val jourService: EtlJourService? = null

    /**
     * 获取指定采集表的所有字段信息
     * @param tid 采集表ID
     * @return 字段列表
     */
    fun getColumns(tid: Long): MutableList<EtlColumn>? {
        return etlColumnRepo!!.findAllByTidOrderByColumnId(tid)
    }

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
    fun updateTableColumns(etlTable: VwEtlTableWithSource?): Int {
        if (etlTable == null) {
            return 0
        }
        val existingColumns: MutableList<EtlColumn>? = etlColumnRepo!!.findAllByTidOrderByColumnId(etlTable.getId())
        if (existingColumns == null || existingColumns.isEmpty()) {
            // 第一次创建，直接全量写入
            return if (createTableColumns(etlTable)) 1 else -1
        }
        ColumnService.log.info("updating table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId())
        val etlJour = jourService!!.addJour(etlTable.getId(), JourKind.UPDATE_COLUMN, null)

        // 获取源表的字段信息
        val hiveTypeMapping = dictService!!.getHiveTypeMapping()
        val sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0"

        var changed = false

        try {
            DriverManager.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass()).use { connection ->
                connection.createStatement().executeQuery(sql).use { rs ->
                    val md = rs.getMetaData()
                    val n = md.getColumnCount()

                    // 构造源端列信息列表（保持顺序）
                    val sourceCols: MutableList<EtlColumn> = ArrayList<EtlColumn>(n)
                    var sc: EtlColumn
                    for (i in 1..n) {
                        sc = getEtlColumn(etlTable, i, md, connection, hiveTypeMapping)
                        sourceCols.add(sc)
                    }

                    // 双指针对齐比较
                    var o = 0 // 当前已经存在的字段索引 index
                    var s = 0 // 当前采集表源字段索引 index
                    val m = existingColumns.size

                    while (o < m && s < n) {
                        val oc = existingColumns.get(o)
                        // 跳过已删除占位
                        if (isDeletedPlaceholder(oc.getColumnName())) {
                            o++
                            continue
                        }
                        sc = sourceCols.get(s)
                        if (oc.getColumnName() == sc.getColumnName()) {
                            // 名称一致 -> 检查类型变化
                            val typeChanged = (oc.getSourceType() != sc.getSourceType()) || notEq(oc.getDataPrecision(), sc.getDataPrecision())
                                    || notEq(oc.getDataScale(), sc.getDataScale())
                                    || oc.getDataLength() !== sc.getDataLength()
                            if (typeChanged) {
                                // 记录风险日志
                                ColumnService.log.warn(
                                    "RISK[COLUMN_TYPE_CHANGE] tid={}, table={}.{}, column={}, srcType: {}(len={},p={},s={}) -> {}(len={},p={},s={})",
                                    etlTable.getId(), etlTable.getSourceDb(), etlTable.getSourceTable(), sc.getColumnName(),
                                    oc.getSourceType(), oc.getDataLength(), nvl(oc.getDataPrecision()), nvl(oc.getDataScale()),
                                    sc.getSourceType(), sc.getDataLength(), nvl(sc.getDataPrecision()), nvl(sc.getDataScale())
                                )
                                // 同步更新字段类型映射
                                oc.setSourceType(sc.getSourceType())
                                oc.setDataLength(sc.getDataLength())
                                oc.setDataPrecision(sc.getDataPrecision())
                                oc.setDataScale(sc.getDataScale())
                                oc.setColComment(sc.getColComment())
                                oc.setTargetType(sc.getTargetType())
                                oc.setTargetTypeFull(sc.getTargetTypeFull())
                                etlColumnRepo.save<EtlColumn?>(oc)
                                changed = true
                            } // else 类型未变化，不做处理

                            o++
                            s++
                        } else {
                            // 名称不一致 -> 视为源删除了 origin 当前位置的列
                            val placeholder: String = DELETED_PLACEHOLDER_PREFIX + oc.getColumnName()
                            oc.setColumnName(placeholder)
                            // 这里要注意该表必须有主键，否则会变成新增记录
                            etlColumnRepo.save<EtlColumn?>(oc)
                            changed = true
                            o++
                            // 注意：不前进 s（新增只允许在末尾追加）
                        }
                    }

                    // 剩余历史列 -> 全部标记为删除占位
                    while (o < m) {
                        val oc = existingColumns.get(o++)
                        if (!isDeletedPlaceholder(oc.getColumnName())) {
                            val placeholder: String = DELETED_PLACEHOLDER_PREFIX + oc.getColumnName()
                            oc.setColumnName(placeholder)
                            etlColumnRepo.save<EtlColumn?>(oc)
                            changed = true
                        }
                    }

                    // 剩余源列 -> 末尾追加为新增列
                    var nextId = m // 现有最大 columnId 基本等于 m（顺序创建）
                    while (s < n) {
                        sc = sourceCols.get(s++)
                        // 复制 sc 的所有属性到 nc，然后只设置 columnId
                        val nc = EtlColumn()
                        BeanUtils.copyProperties(sc, nc)
                        // 只设置 columnId
                        nc.setColumnId(++nextId)
                        etlColumnRepo.save<EtlColumn?>(nc)
                        changed = true
                    }
                }
            }
        } catch (e: SQLException) {
            jourService.failJour(etlJour, e.message)
            ColumnService.log.error("failed to update table columns for tid {}", etlTable.getId(), e)
            return -1
        }
        ColumnService.log.info("table columns updated for tid {}, changed={}", etlTable.getId(), changed)
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
        if (etlTable == null) {
            return false
        }
        ColumnService.log.info("first add table columns for tid {}.{} ({})", etlTable.getSourceDb(), etlTable.getSourceTable(), etlTable.getId())

        val etlJour = jourService!!.addJour(etlTable.getId(), JourKind.CREATE_COLUMN, null)

        val hiveTypeMapping = dictService!!.getHiveTypeMapping()
        val sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0"
        try {
            DriverManager.getConnection(etlTable.getUrl(), etlTable.getUsername(), etlTable.getPass()).use { connection ->
                connection.createStatement().executeQuery(sql).use { resultSet ->
                    val metaData = resultSet.getMetaData()
                    val columnCount = metaData.getColumnCount()
                    for (i in 1..columnCount) {
                        val etlColumn: EtlColumn = getEtlColumn(etlTable, i, metaData, connection, hiveTypeMapping)
                        etlColumnRepo!!.save<EtlColumn?>(etlColumn)
                    }
                    ColumnService.log.info("table columns created for tid {}, total {} columns", etlTable.getId(), columnCount)
                    jourService.successJour(etlJour)
                    return true
                }
            }
        } catch (e: SQLException) {
            jourService.failJour(etlJour, e.message)
            ColumnService.log.error("failed to create table columns for tid {}", etlTable.getId(), e)
            return false
        }
    }

    /**
     * 获取指定采集表的Hive列信息并转换为DDL语句
     * @param tid 采集表ID
     * @return DDL语句列表
     */
    fun getHiveColumnsAsDDL(tid: Long): MutableList<String?> {
        val result: MutableList<String?> = ArrayList<String?>()
        val columns = getColumns(tid)
        for (col in columns!!) {
            val colName: String?
            if (isDeletedPlaceholder(col.getColumnName())) {
                colName = col.getColumnName().substring(DELETED_PLACEHOLDER_PREFIX.length)
            } else {
                colName = col.getColumnName()
            }
            if (col.getColComment().isEmpty()) {
                result.add(colName + " " + col.getTargetTypeFull())
            } else {
                result.add(colName + " " + col.getTargetTypeFull() + " COMMENT '" + nvlStr(col.getColComment()) + "'")
            }
        }
        return result
    }

    /**
     * 根据表ID删除对应的字段信息
     * @param tableId 表ID
     */
    fun deleteByTid(tableId: Long) {
        etlColumnRepo!!.deleteAllByTid(tableId)
    }

    companion object {
        private fun isDeletedPlaceholder(name: String?): Boolean {
            return name != null && name.startsWith(DELETED_PLACEHOLDER_PREFIX)
        }

        private fun notEq(a: Int?, b: Int?): Boolean {
            return a != b
        }

        private fun nvl(v: Int?): Int {
            return if (v == null) 0 else v
        }

        private fun nvlStr(v: String?): String {
            return if (v == null) "" else v
        }

        @Throws(SQLException::class)
        private fun getEtlColumn(
            etlTable: VwEtlTableWithSource,
            i: Int,
            metaData: ResultSetMetaData,
            connection: Connection,
            hiveTypeMapping: MutableMap<String?, String?>
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
                hiveType = String.format("decimal(%d,%d)", metaData.getPrecision(i), metaData.getScale(i))
            }
            etlColumn.setTargetTypeFull(hiveType)
            return etlColumn
        }
    }
}
