package com.wgzhao.addax.admin.service.impl

import com.wgzhao.addax.admin.common.JourKind
import com.wgzhao.addax.admin.model.VwEtlTableWithSource
import com.wgzhao.addax.admin.service.ColumnService
import com.wgzhao.addax.admin.service.DictService
import com.wgzhao.addax.admin.service.EtlJourService
import com.wgzhao.addax.admin.service.TargetService
import lombok.extern.slf4j.Slf4j
import org.apache.commons.dbcp2.BasicDataSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.lang.String
import java.net.URL
import java.net.URLClassLoader
import java.sql.Connection
import java.sql.SQLException
import java.text.MessageFormat
import javax.sql.DataSource
import kotlin.Array
import kotlin.Boolean
import kotlin.Exception
import kotlin.Long
import kotlin.RuntimeException
import kotlin.arrayOf
import kotlin.concurrent.Volatile
import kotlin.synchronized
import kotlin.text.trimIndent
import kotlin.use

@Service
@Slf4j
class TargetServiceWithHiveImpl

    : TargetService {
    @Autowired
    private val dictService: DictService? = null

    @Autowired
    private val columnService: ColumnService? = null

    @Autowired
    private val jourService: EtlJourService? = null

    @Value("\${spring.datasource.hive.url}")
    private val url: String? = null

    @Value("\${spring.datasource.hive.username}")
    private val username: String? = null

    @Value("\${spring.datasource.hive.password}")
    private val password: String? = null

    @Value("\${spring.datasource.hive.driver-class-name}")
    private val driverClassName: String? = null

    @Value("\${spring.datasource.hive.jar-path}")
    private val driverPath: String? = null

    //    private DataSource hiveDataSource;
    @Volatile
    private var hiveDataSource: DataSource? = null

    private fun getHiveDataSource(): Connection {
        if (hiveDataSource == null) {
            synchronized(this) {
                if (hiveDataSource == null) {
                    try {
                        TargetServiceWithHiveImpl.log.info("try to load hive jdbc driver from {}", driverPath)
                        val hiveJarFile = File(driverPath)
                        val jarUrls: Array<URL?> = arrayOf<URL>(hiveJarFile.toURI().toURL())
                        // 创建独立的类加载器
                        val classLoader = URLClassLoader(jarUrls, this.javaClass.getClassLoader())

                        // 设置 Hive JDBC 驱动的类加载器
                        Thread.currentThread().setContextClassLoader(classLoader)

                        val dataSource = BasicDataSource()
                        dataSource.setUrl(url)
                        dataSource.setUsername(username)
                        dataSource.setPassword(password)
                        dataSource.setDriverClassName(driverClassName)

                        hiveDataSource = dataSource
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
            }
        }
        try {
            return hiveDataSource!!.getConnection()
        } catch (e: Exception) {
            throw RuntimeException("Failed to get connection from Hive DataSource", e)
        }
    }

    /**
     * 为指定 Hive 表添加分区。
     *
     * @param taskId 采集任务ID
     * @param db Hive数据库名
     * @param table Hive表名
     * @param partName 分区字段名
     * @param partValue 分区字段值
     * @return 是否添加成功
     */
    override fun addPartition(taskId: Long, db: String?, table: String?, partName: String?, partValue: String?): Boolean {
        val sql = String.format("ALTER TABLE %s.%s ADD IF NOT EXISTS PARTITION (%s='%s')", db, table, partName, partValue)
        val etlJour = jourService!!.addJour(taskId, JourKind.PARTITION, sql)
        try {
            getHiveDataSource().use { conn ->
                conn.createStatement().use { stmt ->
                    TargetServiceWithHiveImpl.log.info("Add partition for {}.{}: {}", db, table, sql)
                    stmt.execute(sql)
                    jourService.successJour(etlJour)
                    return true
                }
            }
        } catch (e: SQLException) {
            TargetServiceWithHiveImpl.log.error("Failed to add partition ", e)
            jourService.failJour(etlJour, e.message)
            return false
        }
    }

    /**
     * 创建或更新 Hive 目标表。
     * 包括建库、建表、分区、表属性等操作。
     *
     * @param etlTable 采集表视图对象
     * @return 是否创建/更新成功
     */
    override fun createOrUpdateHiveTable(etlTable: VwEtlTableWithSource): Boolean {
        val hiveColumns = columnService!!.getHiveColumnsAsDDL(etlTable.getId())

        val createDbSql = MessageFormat.format(
            "create database if not exists `{0}` location ''{1}/{0}''",
            etlTable.getTargetDb(), dictService!!.hdfsPrefix
        )
        val createTableSql = MessageFormat.format(
            """
                        create external table if not exists `{0}`.`{1}` (
                        {2}
                        ) comment ''{3}''
                        partitioned by ( `{4}` string )
                         stored as {5}
                         location ''{6}/{0}/{1}''
                         tblproperties (''external.table.purge''=''true'', ''discover.partitions''=''true'', ''orc.compress''=''{7}'', ''snappy.compress''=''{7}'')
                        
                        """.trimIndent(),
            etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(),
            etlTable.getPartName(), dictService.hdfsStorageFormat, dictService.hdfsPrefix, dictService.hdfsCompress
        )

        TargetServiceWithHiveImpl.log.info("create table sql:\n{}", createTableSql)
        val etlJour = jourService!!.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql)
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
            TargetServiceWithHiveImpl.log.warn("Failed to create or update hive table ", e)
            jourService.failJour(etlJour, e.message)
            return false
        }
    }
}
