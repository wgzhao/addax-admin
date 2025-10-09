package com.wgzhao.addax.admin.config;

import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

//@Configuration
//@Conditional(HiveJdbcCondition.class)
public class HiveDataSourceConfig {

    @Value("${spring.datasource.hive.url}")
    private String url;

    @Value("${spring.datasource.hive.username}")
    private String username;

    @Value("${spring.datasource.hive.password}")
    private String password;

    @Value("${spring.datasource.hive.driver-class-name}")
    private String driverClassName;

    private static final String driversDir = "lib";

    @Bean(name = "hiveDataSource")
    public DataSource hiveDataSource() throws Exception{

        File driversFolder = new File(driversDir);
        if (!driversFolder.exists() || !driversFolder.isDirectory()) {
            throw new IllegalArgumentException("Drivers directory does not exist: " + driversDir);
        }

        File[] jarFiles = driversFolder.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            throw new IllegalArgumentException("No JDBC driver found in drivers directory: " + driversDir);
        }

        URL[] jarUrls = new URL[jarFiles.length];
        for (int i = 0; i < jarFiles.length; i++) {
//            jarUrls[i] = jarFiles[i].toURI().toURL();
            try (URLClassLoader tempLoader = new URLClassLoader(new URL[]{jarFiles[i].toURI().toURL()})) {
                Class.forName(driverClassName, true, tempLoader);
                // 如果找到驱动类，加载成功
                break;
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Invalid Hive JDBC driver: " + jarFiles[i].getName());
            }
        }

        // 创建独立的类加载器
        URLClassLoader classLoader = new URLClassLoader(jarUrls, this.getClass().getClassLoader());

        // 设置 Hive JDBC 驱动的类加载器
        Thread.currentThread().setContextClassLoader(classLoader);
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }
}