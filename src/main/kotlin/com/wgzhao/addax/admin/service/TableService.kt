package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.common.TableStatus
import com.wgzhao.addax.admin.dto.TaskResultDto
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.failure
import com.wgzhao.addax.admin.dto.TaskResultDto.Companion.success
import com.wgzhao.addax.admin.model.EtlTable
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.repository.EtlTableRepo
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo
import com.wgzhao.addax.admin.utils.QueryUtil
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import java.util.function.Supplier
import kotlin.math.max

/**
 * 采集表信息服务类，负责采集表的增删改查及资源刷新等业务操作
 */
@Service
open class TableService(
    private val etlTableRepo: EtlTableRepo,
    private val columnService: ColumnService,
    private val jobContentService: JobContentService,
    private val dictService: DictService,
    private val jourService: EtlJourService,
    private val vwEtlTableWithSourceRepo: VwEtlTableWithSourceRepo,
    private val targetService: TargetService
) {
    private val log = LoggerFactory.getLogger(TableService::class.java)

    /**
     * 刷新指定采集表的资源（如字段、模板等）
     * @param table 采集表对象
     * @return 任务结果
     */
    fun refreshTableResources(table: EtlTable?): TaskResultDto {
        if (table == null) return failure("Table is null", 0)
        val vwTable = vwEtlTableWithSourceRepo.findById(table.id).get()
        val retCode = columnService.updateTableColumns(vwTable)
        if (retCode == -1) {
            setStatus(table, TableStatus.COLLECT_FAIL)
            log.warn("Failed to update columns for table id {}", table.id)
            return failure("Failed to update columns for table id ${table.id}", 0)
        }
        if (retCode == 0) {
            if (vwTable.status == TableStatus.WAIT_SCHEMA) {
                if (!targetService.createOrUpdateHiveTable(vwTable)) {
                    setStatus(table, TableStatus.COLLECT_FAIL)
                    log.warn("Failed to create or update Hive table for tid {}", table.id)
                    return failure("Failed to create or update Hive table for tid ${table.id}", 0)
                }
            }
            setStatus(table, TableStatus.NOT_COLLECT)
            return success("No columns updated for table id ${table.id}", 0)
        }
        if (!targetService.createOrUpdateHiveTable(vwTable)) {
            setStatus(table, TableStatus.WAIT_SCHEMA)
            log.warn("Failed to create or update Hive table for tid {}", table.id)
            return failure("Failed to create or update Hive table for tid ${table.id}", 0)
        }
        val result = jobContentService.updateJob(vwTable)
        if (result.success) {
            setStatus(table, TableStatus.NOT_COLLECT)
            return success("Table resources refreshed successfully", 0)
        } else {
            setStatus(table, TableStatus.COLLECT_FAIL)
            return failure("Failed to update job content for table id ${table.id}", 0)
        }
    }

    /**
     * 刷新指定ID的采集表资源
     * @param tableId 采集表ID
     * @return 任务结果
     */
    fun refreshTableResources(tableId: Long): TaskResultDto {
        val table = etlTableRepo.findById(tableId)
            .orElseThrow<IllegalArgumentException?>(Supplier { IllegalArgumentException("Table not found with id: " + tableId) })
        return refreshTableResources(table)
    }

    /**
     * 异步刷新采集表资源
     * @param table 采集表对象
     */
    @Async
    open fun refreshTableResourcesAsync(table: EtlTable?) {
        refreshTableResources(table)
    }

    /**
     * 刷新所有采集表的资源
     */
    fun refreshAllTableResources() {
        etlTableRepo.findAll().map {
            refreshTableResources(it)
        }
    }

    /**
     * ODS 采集信息
     *
     */
    fun getVwTablesInfo(page: Int, pageSize: Int, q: String?, sortField: String?, sortOrder: String?): Page<VwEtlTableWithSource?>? {
        val pageable: Pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder))
        if (q != null && !q.isEmpty()) {
            return vwEtlTableWithSourceRepo.findByFilterColumnContaining(q.uppercase(Locale.getDefault()), pageable)
        } else {
            return vwEtlTableWithSourceRepo.findAll(pageable)
        }
    }

    /**
     * 根据状态获取视图表信息
     * @param page 页码
     * @param pageSize 每页大小
     * @param q 查询关键字
     * @param status 表状态
     * @param sortField 排序字段
     * @param sortOrder 排序方式
     * @return 视图表信息分页结果
     */
    fun getVwTablesByStatus(page: Int, pageSize: Int, q: String?, status: String?, sortField: String?, sortOrder: String?): Page<VwEtlTableWithSource?>? {
        val pageable: Pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder))
        return vwEtlTableWithSourceRepo.findByStatusAndFilterColumnContaining(status, q?.uppercase(Locale.getDefault()), pageable)
    }

    /**
     * 获取单个表的详细信息
     * @param tid 表ID
     * @return 视图表对象
     */
    fun findOneTableInfo(tid: Long): VwEtlTableWithSource? {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null)
    }

    // 找到所有需要采集的表
    /**
     * 统计所有待采集任务数量
     * @return 待采集任务数量
     */
    fun findPendingTasks(): Int {
        return etlTableRepo.countByStatusEquals(TableStatus.NOT_COLLECT)
    }

    /**
     * 统计所有正在运行的任务数量
     * @return 正在运行的任务数量
     */
    fun findRunningTasks(): Int {
        return etlTableRepo.countByStatusEquals(TableStatus.COLLECTING)
    }

    /**
     * 根据ID获取表及其数据源信息
     * @param tid 表ID
     * @return 表对象
     */
    fun getTableAndSource(tid: Long): EtlTable? {
        return etlTableRepo.findById(tid).orElse(null)
    }

    /**
     * 根据ID获取表信息
     * @param tid 表ID
     * @return 表对象
     */
    fun getTable(tid: Long): EtlTable? {
        return etlTableRepo.findById(tid).orElse(null)
    }

    /**
     * 根据ID获取视图表信息
     * @param tid 表ID
     * @return 视图表对象
     */
    fun getTableView(tid: Long): VwEtlTableWithSource? {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null)
    }

    /**
     * 设置任务为正在运行状态
     * @param task 任务对象
     */
    fun setRunning(task: EtlTable) {
        task.status = TableStatus.COLLECTING
        task.startTime =  Timestamp(System.currentTimeMillis())
        etlTableRepo.save<EtlTable?>(task)
    }

    /**
     * 设置任务为已完成状态
     * @param task 任务对象
     */
    fun setFinished(task: EtlTable) {
        task.status = TableStatus.COLLECTED
        // 重试次数也重置
        task.retryCnt = 3
        task.endTime = Timestamp(System.currentTimeMillis())
        etlTableRepo.save<EtlTable?>(task)
    }

    /**
     * 设置任务为失败状态
     * @param task 任务对象
     */
    fun setFailed(task: EtlTable) {
        task.status = TableStatus.COLLECT_FAIL
        task.endTime = Timestamp(System.currentTimeMillis())
        task.retryCnt = max(task.retryCnt.minus(1) , 0)
        etlTableRepo.save<EtlTable?>(task)
    }

    /**
     * 设置任务状态
     * @param table 任务对象
     * @param status 状态值
     */
    fun setStatus(table: EtlTable, status: String?) {
        table.status = status
        etlTableRepo.save<EtlTable?>(table)
    }

    // 找到所有可以运行的任务
    // 要注意切日的问题
    val runnableTasks: MutableList<EtlTable?>?
        /**
         * 获取所有可运行的任务
         * @return 可运行的��务列表
         */
        get() {
            val switchTime = dictService.switchTimeAsTime
            val currentTime = LocalDateTime.now().toLocalTime()
            val checkTime = currentTime.isAfter(switchTime)
            return etlTableRepo.findRunnableTasks(switchTime, currentTime, checkTime)
        }

    /**
     * 根据数据源ID获取可运行的任务
     * @param sourceId 数据源ID
     * @return 可运行的任务列表
     */
    fun getRunnableTasks(sourceId: Int): MutableList<EtlTable?> {
        val switchTime = dictService.switchTimeAsTime
        val currentTime = LocalDateTime.now().toLocalTime()
        val checkTime = currentTime.isAfter(switchTime)
        return etlTableRepo.findRunnableTasks(switchTime, currentTime, checkTime)!!
            .stream()
            .filter { t: EtlTable? -> t.sid == sourceId }
            .toList()
    }

    val validTableCount: Int?
        /**
         * 获取有效表的数量
         * @return 有效表的数量
         */
        get() = etlTableRepo.findValidTableCount()

    val validTables: MutableList<EtlTable?>?
        /**
         * 获取所有有效表
         * @return 有效表列表
         */
        get() = etlTableRepo.findValidTables()

    val validTableViews: MutableList<VwEtlTableWithSource?>?
        /**
         * 获取所有有效的视图表
         * @return 视图表列表
         */
        get() = vwEtlTableWithSourceRepo.findByEnabledTrueAndStatusNot(TableStatus.EXCLUDE_COLLECT)

    /**
     * 重置所有表的标志位
     */
    @Transactional
    open fun resetAllFlags() {
        etlTableRepo.resetAllEtlFlags()
    }

    /**
     * 查找特殊任务
     * @return 特殊任务列表
     */
    fun findSpecialTasks(): MutableList<EtlTable?>? {
        return etlTableRepo.findSpecialTasks()
    }

    /**
     * 获取指定数据源和库下的所有表
     */
    fun getTablesBySidAndDb(sid: Int, db: String?): List<String>? {
        return vwEtlTableWithSourceRepo.findBySidAndSourceDb(sid, db)
            ?.mapNotNull { it.sourceTable }
    }

    /**
     * 删除指定ID的表及其相关信息
     * @param tableId 表ID
     */
    @Transactional
    open fun deleteTable(tableId: Long) {
        // 首先删除列信息
        columnService.deleteByTid(tableId)
        // 然后删除任务信息
        jobContentService.deleteByTid(tableId)
        // 删除流水
        jourService.deleteByTid(tableId)
        // 最后删除采集表信息
        etlTableRepo.deleteById(tableId)
    }

    /**
     * 创建新的采集表
     * @param etl 采集表对象
     * @return 创建的采集表对象
     */
    fun createTable(etl: EtlTable): EtlTable {
        return etlTableRepo.save<EtlTable>(etl)
    }

    /**
     * 批量创建采集表
     * @param tables 采集表对象列表
     * @return 创建的采集表对象列表
     */
    fun batchCreateTable(tables: MutableList<EtlTable?>): MutableList<EtlTable?> {
        return etlTableRepo.saveAll<EtlTable?>(tables)
    }

    /**
     * 根据数据源ID获取采集表数量
     * @param sid
     */
    fun getTableCountBySourceId(sid: Int): Int {
        return etlTableRepo.countBySid(sid)
    }
}
