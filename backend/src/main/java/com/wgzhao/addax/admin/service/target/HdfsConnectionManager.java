package com.wgzhao.addax.admin.service.target;

import tools.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.JdbcDriverRegistry;
import com.wgzhao.addax.admin.model.EtlTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Opens HiveServer2 connections for a specific HDFS target.
 *
 * <p>Hive JDBC drivers normally live in {@code ${APP_HOME}/drivers} and are resolved through the
 * isolated {@link JdbcDriverRegistry}. Some vendor "standalone" jars bundle their own
 * Tomcat/SLF4J classes; for those the target may configure an optional {@code driverPath}, which
 * the registry loads in its own classloader without touching the application classpath. The driver
 * is only used by addax-admin for DDL/metadata work — Addax's own hdfswriter does not need it.</p>
 *
 * <p>A refresh round reuses one connection per endpoint through {@link #beginRound()}. A daily
 * refresh asks the target for a MAX value once per incremental table, and a fresh HiveServer2
 * session pays for warming its metastore/partition cache on every call: measured ~10s cold versus
 * ~1-2s warm against the same cluster. Per-run work ({@code prepareBeforeRun}, a manual single-table
 * refresh) keeps opening and closing a connection as before.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HdfsConnectionManager
{
    private final ObjectMapper objectMapper;
    private final JdbcDriverRegistry driverRegistry;

    /** Connections owned by the current refresh round, keyed by target id. Absent outside a round. */
    private final ThreadLocal<Map<Long, Connection>> roundConnections = new ThreadLocal<>();

    public void beginRound()
    {
        endRound();
        roundConnections.set(new LinkedHashMap<>());
    }

    /** Closes every connection the round opened. Callers must not use them afterwards. */
    public void endRound()
    {
        Map<Long, Connection> connections = roundConnections.get();
        if (connections == null) {
            return;
        }
        connections.forEach((targetId, connection) -> closeQuietly(targetId, connection));
        roundConnections.remove();
    }

    /**
     * Drops a connection that failed mid-round so the next call opens a fresh session instead of
     * reusing a broken one for every remaining table.
     */
    public void invalidate(EtlTarget target)
    {
        Map<Long, Connection> connections = roundConnections.get();
        if (connections == null || target == null) {
            return;
        }
        Connection broken = connections.remove(target.getId());
        if (broken != null) {
            closeQuietly(target.getId(), broken);
        }
    }

    public Connection open(EtlTarget target) throws SQLException
    {
        Map<Long, Connection> connections = roundConnections.get();
        if (connections == null) {
            return openNew(target);
        }
        Connection existing = connections.get(target.getId());
        if (existing != null && !existing.isClosed()) {
            return nonClosing(existing);
        }
        if (existing != null) {
            connections.remove(target.getId());
            closeQuietly(target.getId(), existing);
        }
        Connection fresh = openNew(target);
        connections.put(target.getId(), fresh);
        return nonClosing(fresh);
    }

    private static void closeQuietly(Long targetId, Connection connection)
    {
        try {
            connection.close();
        }
        catch (SQLException e) {
            log.warn("Failed to close Hive connection for target {}: {}", targetId, e.getMessage());
        }
    }

    /**
     * Round-scoped call sites keep their try-with-resources shape; closing the handle must not close
     * the shared session, only {@link #endRound()}/{@link #invalidate(EtlTarget)} may do that.
     */
    private static Connection nonClosing(Connection delegate)
    {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
                if ("close".equals(method.getName())) {
                    return null;
                }
                try {
                    return method.invoke(delegate, args);
                }
                catch (InvocationTargetException e) {
                    throw e.getTargetException();
                }
            });
    }

    private Connection openNew(EtlTarget target) throws SQLException
    {
        HdfsTargetSettings settings = HdfsTargetSettings.from(target, objectMapper);
        if (settings.url() == null || settings.url().isBlank())
        {
            throw new SQLException("目标端未配置 HiveServer2 JDBC 地址: " + target.getName());
        }
        if (settings.driverPath() != null && !settings.driverPath().isBlank())
        {
            if (new File(settings.driverPath()).isFile())
            {
                return connectWithConfiguredDriver(settings);
            }
            log.warn("Configured Hive driver jar {} not found for target {}; falling back to the "
                + "shared drivers directory", settings.driverPath(), target.getName());
        }
        try
        {
            // Fail fast with an actionable message instead of the generic "No suitable driver" from
            // DriverManager.getConnection, which does not say where the jar is expected.
            DriverManager.getDriver(settings.url());
        }
        catch (SQLException e)
        {
            log.warn("No JDBC driver accepts Hive url {} for target {}: {}",
                settings.url(), target.getName(), e.getMessage());
            throw new SQLException("未找到 Hive JDBC 驱动，请将 hive-jdbc*.jar 放入 "
                + "${APP_HOME}/drivers 后重启，或为目标端配置驱动 jar 路径: " + e.getMessage(),
                e.getSQLState(), e);
        }
        return DriverManager.getConnection(settings.url(), settings.username(), settings.password());
    }

    private Connection connectWithConfiguredDriver(HdfsTargetSettings settings) throws SQLException
    {
        Driver driver = driverRegistry.loadExternalDriver(settings.driverPath(), settings.driverClassName());
        Properties properties = new Properties();
        if (settings.username() != null && !settings.username().isBlank())
        {
            properties.setProperty("user", settings.username());
        }
        if (settings.password() != null && !settings.password().isBlank())
        {
            properties.setProperty("password", settings.password());
        }
        Connection connection = driver.connect(settings.url(), properties);
        if (connection == null)
        {
            throw new SQLException("配置的 Hive JDBC 驱动不接受连接地址: " + settings.url());
        }
        return connection;
    }
}
