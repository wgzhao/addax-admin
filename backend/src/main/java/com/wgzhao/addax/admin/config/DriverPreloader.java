package com.wgzhao.addax.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Preload JDBC drivers at application startup to ensure DriverManager can discover
 * drivers shipped as external jars via PropertiesLauncher/loader.path.
 */
@Slf4j
//@Configuration
public class DriverPreloader {

    /**
     * Optional additional driver class names configurable via properties.
     * Example: addax.jdbc.extraDrivers=com.ibm.db2.jcc.DB2Driver
     */
    @Value("${addax.jdbc.extraDrivers:}")
    private String extraDrivers;

    @PostConstruct
    public void preload() {
        List<String> drivers = new ArrayList<>(Arrays.asList(
                // SQL Server
                "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                // MySQL
                "com.mysql.cj.jdbc.Driver",
                // PostgreSQL
                "org.postgresql.Driver",
                // Oracle
                "oracle.jdbc.OracleDriver",
                // ClickHouse (new and legacy packages)
                "com.clickhouse.jdbc.ClickHouseDriver",
                "ru.yandex.clickhouse.ClickHouseDriver"
        ));
        if (extraDrivers != null && !extraDrivers.isBlank()) {
            for (String d : extraDrivers.split(",")) {
                String cls = d.trim();
                if (!cls.isEmpty()) drivers.add(cls);
            }
        }
        int success = 0;
        for (String driver : drivers) {
            try {
                Class.forName(driver);
                success++;
                log.info("Preloaded JDBC driver: {}", driver);
            } catch (ClassNotFoundException e) {
                log.debug("JDBC driver not present: {}", driver);
            } catch (Throwable t) {
                log.warn("Failed to preload JDBC driver: {}", driver, t);
            }
        }
        log.info("JDBC driver preload finished. Loaded {}/{} candidates.", success, drivers.size());
    }
}

