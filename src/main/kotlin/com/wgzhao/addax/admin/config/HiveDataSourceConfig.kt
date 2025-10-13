package com.wgzhao.addax.admin.config

import org.apache.commons.dbcp2.BasicDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import java.io.File
import java.io.FilenameFilter
import java.net.URL
import java.net.URLClassLoader
import javax.sql.DataSource

//@Configuration
//@Conditional(HiveJdbcCondition.class)
class HiveDataSourceConfig {
    @Value("\${spring.datasource.hive.url}")
    private val url: String? = null

    @Value("\${spring.datasource.hive.username}")
    private val username: String? = null

    @Value("\${spring.datasource.hive.password}")
    private val password: String? = null

    @Value("\${spring.datasource.hive.driver-class-name}")
    private val driverClassName: String? = null

    @Bean(name = ["hiveDataSource"])
    @Throws(Exception::class)
    fun hiveDataSource(): DataSource {
        val driversFolder: File = File(driversDir)
        require(!(!driversFolder.exists() || !driversFolder.isDirectory())) { "Drivers directory does not exist: " + driversDir }

        val jarFiles = driversFolder.listFiles(FilenameFilter { dir: File?, name: String? -> name!!.endsWith(".jar") })
        require(!(jarFiles == null || jarFiles.size == 0)) { "No JDBC driver found in drivers directory: " + driversDir }

        val jarUrls = arrayOfNulls<URL>(jarFiles.size)
        for (i in jarFiles.indices) {
//            jarUrls[i] = jarFiles[i].toURI().toURL();
            try {
                URLClassLoader(arrayOf<URL>(jarFiles[i]!!.toURI().toURL())).use { tempLoader ->
                    Class.forName(driverClassName, true, tempLoader)
                    // 如果找到驱动类，加载成功
                    break
                }
            } catch (e: ClassNotFoundException) {
                throw IllegalArgumentException("Invalid Hive JDBC driver: " + jarFiles[i]!!.getName())
            }
        }

        // 创建独立的类加载器
        val classLoader = URLClassLoader(jarUrls, this.javaClass.getClassLoader())

        // 设置 Hive JDBC 驱动的类加载器
        Thread.currentThread().setContextClassLoader(classLoader)
        val dataSource = BasicDataSource()
        dataSource.setUrl(url)
        dataSource.setUsername(username)
        dataSource.setPassword(password)
        dataSource.setDriverClassName(driverClassName)
        return dataSource
    }

    companion object {
        private const val driversDir = "lib"
    }
}