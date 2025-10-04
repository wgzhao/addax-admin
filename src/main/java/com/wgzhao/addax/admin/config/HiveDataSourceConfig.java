package com.wgzhao.addax.admin.config;

import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class HiveDataSourceConfig {

    @Value("${spring.datasource.hive.url}")
    private String url;

    @Value("${spring.datasource.hive.username}")
    private String username;

    @Value("${spring.datasource.hive.password}")
    private String password;

    @Value("${spring.datasource.hive.driver-class-name}")
    private String driverClassName;

    @Bean(name = "hiveDataSource")
    public DataSource hiveDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }
}