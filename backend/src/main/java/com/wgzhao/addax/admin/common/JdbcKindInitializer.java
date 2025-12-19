package com.wgzhao.addax.admin.common;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Initialize JDBC_KIND_MAP in Constants at application startup.
 * This allows configuration-based initialization in the future.
 */
@Component
public class JdbcKindInitializer {

    @PostConstruct
    public void init() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("jdbc:mysql", "mysql");
        m.put("jdbc:oracle", "oracle");
        m.put("jdbc:sqlserver", "sqlserver");
        m.put("jdbc:postgresql", "postgresql");
        m.put("jdbc:db2", "db2");
        m.put("jdbc:clickhouse", "clickhouse");
        m.put("jdbc/chk", "clickhouse");
        Constants.JDBC_KIND_MAP = java.util.Collections.unmodifiableMap(m);
    }
}

