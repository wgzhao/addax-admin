package com.wgzhao.addax.admin.service.impl

import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.service.ColumnService
import com.wgzhao.addax.admin.service.DictService
import com.wgzhao.addax.admin.service.EtlJourService
import com.wgzhao.addax.admin.service.SystemConfigService
import com.wgzhao.addax.admin.service.TargetService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.dbcp2.BasicDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.net.URLClassLoader
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource
import kotlin.concurrent.Volatile

@Service
class TargetServiceWithHiveImpl(
    private val dictService: DictService,
    private val columnService: ColumnService,
    private val jourService: EtlJourService,
    private val configService: SystemConfigService,
) : TargetService {

    private val logger = KotlinLogging.logger {}


    @Value("\${spring.datasource.hive.url}")
    lateinit var  url: String

    @Value("\${spring.datasource.hive.username}")
    private val username: String? = null

    @Value("\${spring.datasource.hive.password}")
    private val password: String? = null

    @Value("\${spring.datasource.hive.driver-class-name}")
    private val driverClassName: String? = null

    @Value("\${spring.datasource.hive.jar-path}")
    lateinit var driverPath: String

    @Volatile
    private var hiveDataSource: DataSource? = null

    private fun getHiveDataSource(): Connection {
        if (hiveDataSource == null) {
            synchronized(this) {
                if (hiveDataSource == null) {
                    try {
                        logger.info { "try to load hive jdbc driver from $driverPath" }
                        val hiveJarFile = File(driverPath)
                        val jarUrls = arrayOf(hiveJarFile.toURI().toURL())
                        val classLoader = URLClassLoader(jarUrls, this.javaClass.classLoader)
                        Thread.currentThread().contextClassLoader = classLoader
                        val dataSource = BasicDataSource()
                        dataSource.url = url
                        dataSource.username = username
                        dataSource.password = password
                        dataSource.driverClassName = driverClassName
                        hiveDataSource = dataSource
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        try {
            return hiveDataSource!!.connection
        } catch (e: Exception) {
            throw RuntimeException("Failed to get connection from Hive DataSource", e)
        }
    }

    override fun addPartition(taskId: Long, db: String, table: String, partName: String, partValue: String): Boolean {
        val sql = "ALTER TABLE $db.$table ADD IF NOT EXISTS PARTITION ($partName='$partValue')"
        val etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql)
        try {
            getHiveDataSource().use { conn ->
                conn.createStatement().use { stmt ->
                    logger.info { "Add partition for $db.$table: $sql" }
                    stmt.execute(sql)
                    jourService.successJour(etlJour)
                    return true
                }
            }
        } catch (e: SQLException) {
            logger.error(e) { "Failed to add partition" }
            jourService.failJour(etlJour, e.message)
            return false
        }
    }

    override fun createOrUpdateHiveTable(etlTable: VwEtlTableWithSource): Boolean {
        val hiveColumns = columnService.getHiveColumnsAsDDL(etlTable.id)
        val createDbSql = "create database if not exists `${etlTable.targetDb}` location '${configService.hdfsPrefix}/${etlTable.targetDb}'"
        val createTableSql = """
            create external table if not exists `${etlTable.targetDb}`.`${etlTable.targetTable}` (
            ${hiveColumns.joinToString(",\n")}
            ) comment '${etlTable.tblComment}'
            partitioned by ( `${etlTable.partName}` string )
            stored as ${etlTable.storageFormat}
            location '${configService.hdfsPrefix}/${etlTable.targetDb}/${etlTable.targetTable}'
            tblproperties ('external.table.purge'='true', 'discover.partitions'='true', 'orc.compress'='${etlTable.compressFormat}', 'snappy.compress'='${etlTable.compressFormat}')
        """.trimIndent()
        logger.info { "create table sql:\n$createTableSql" }
        val etlJour = jourService.addJour(etlTable.id, JourKind.UPDATE_TABLE, createTableSql)
        try {
            getHiveDataSource().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(createDbSql)
                    stmt.execute(createTableSql)
                    jourService.successJour(etlJour)
                    return true
                }
            }
        } catch (e: SQLException) {
            logger.warn(e) { "Failed to create or update hive table" }
            jourService.failJour(etlJour, e.message)
            return false
        }
    }
}
