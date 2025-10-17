package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.dto.TableMetaDto
import com.wgzhao.addax.admin.event.SourceUpdatedEvent
import com.wgzhao.addax.admin.model.EtlSource
import com.wgzhao.addax.admin.repository.EtlSourceRepo
import com.wgzhao.addax.admin.utils.DbUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

/**
 * 数据源服务类，负责数据源的增删改查及相关元数据操作。
 */
@Service
class SourceService(
    private val etlSourceRepo: EtlSourceRepo,
    private val collectionSchedulingService: CollectionSchedulingService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = KotlinLogging.logger {}

    /**
     * 获取有效数据源数量
     *
     * @return 有效数据源数量
     */
    fun getValidSources(): Int = etlSourceRepo.countByEnabled(true) ?: 0

    /**
     * 根据ID获取数据源对象
     *
     * @param sid 数据源ID
     * @return 数据源对象或null
     */
    fun getSource(sid: Int): EtlSource? = etlSourceRepo.findById(sid).orElse(null)

    /**
     * 检查数据源编号是否存在
     *
     * @param code 数据源编号
     * @return 是否存在
     */
    fun checkCode(code: String?): Boolean = etlSourceRepo.existsByCode(code)

    /**
     * 查询所有数据源
     *
     * @return 数据源列表
     */
    fun findAll(): List<EtlSource> = etlSourceRepo.findAll().filterNotNull()

    /**
     * 根据ID查询数据源
     *
     * @param id 数据源ID
     * @return 可选数据源对象
     */
    fun findById(id: Int): Optional<EtlSource> = Optional.ofNullable(etlSourceRepo.findById(id).orElse(null))

    /**
     * 保存数据源对象，并根据需要更新调度任务
     *
     * @param etlSource 数据源对象
     * @return 保存后的数据源对象
     */
    fun save(etlSource: EtlSource): EtlSource {
        // 需要和现有数据的调度时间进行对比，如果不相同，则还需要更新调度时间
        val existing = etlSourceRepo.findById(etlSource.id).get()
        etlSourceRepo.save(etlSource)
        val scheduleChanged = existing.startAt != etlSource.startAt

        // 更新该采集源下所有采集任务的模板，这里主要考虑到可能调整了采集源的连接参数
        // 如果连接串，账号，密码三者没变更，则不要更新任务模板
        val existPos: String = (existing.url ?: "") + (existing.username ?: "") + (existing.pass ?: "")
        val newPos: String = (etlSource.url ?: "") + (etlSource.username ?: "") + (etlSource.pass ?: "")
        val connectionChanged = existPos != newPos
        if (scheduleChanged || connectionChanged) {
            val sourceUpdatedEvent = SourceUpdatedEvent(this, etlSource.id, connectionChanged, scheduleChanged)
            eventPublisher.publishEvent(sourceUpdatedEvent)
        }
        return etlSource
    }

    /**
     * 根据ID删除数据源
     *
     * @param id 数据源ID
     */
    fun deleteById(id: Int) {
        // 删除之前，应该先取消该数据源的调度任务
        etlSourceRepo.findById(id).ifPresent { etlSource ->
             collectionSchedulingService.cancelTask(etlSource.code)
        }
        etlSourceRepo.deleteById(id)
    }

    /**
     * 检查数据源ID是否存在
     *
     * @param id 数据源ID
     * @return 是否存在
     */
    fun existsById(id: Int): Boolean = etlSourceRepo.existsById(id)

    /**
     * 批量保存数据源对象
     *
     * @param sources 数据源列表
     */
    fun saveAll(sources: MutableList<EtlSource?>) {
        etlSourceRepo.saveAll(sources)
    }

    /**
     * 新建数据源对象，并自动创建调度任务
     *
     * @param etlSource 数据源对象
     * @return 保存后的数据源对象
     */
    fun create(etlSource: EtlSource): EtlSource = etlSourceRepo.save(etlSource)

    /**
     * 获取指定数据库下未采集的表元数据（含表注释）
     *
     * @param source 数据源对象
     * @param dbName 数据库名
     * @param existsSet 已采集表集合
     * @return 未采集表元数据列表
     */
    fun getUncollectedTables(source: EtlSource, dbName: String?, existsSet: Set<String?>): MutableList<TableMetaDto?>? {
        val result: MutableList<TableMetaDto?> = ArrayList<TableMetaDto?>()
        try {
            DriverManager.getConnection(source.url, source.username, source.pass).use { connection ->
                // 按元数据读取所有表
                val tables = connection.metaData.getTables(dbName, null, "%", arrayOf("TABLE"))
                while (tables.next()) {
                    val tblName = tables.getString("TABLE_NAME")
                    // 已采集表跳过
                    if (existsSet.contains(tblName) || existsSet.contains(tblName.lowercase(Locale.getDefault()))) {
                        continue
                    }
                    var remarks = tables.getString("REMARKS") ?: ""
                    // 优先使用元数据中的注释，否则回退到 commentFallback
                    if (remarks.isEmpty()) {
                        remarks = DbUtil.getTableComment(connection, dbName, tblName)
                    }
                    result.add(TableMetaDto(tblName, remarks))
                }
            }
        } catch (e: SQLException) {
            log.warn(e) { "Failed to get uncollected tables for source ${source.id}: ${e.message}" }
            return null
        }
        return result
    }
}
