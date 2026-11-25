package com.wgzhao.addax.admin.config.schema;

import com.wgzhao.addax.admin.common.JdbcDriverRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-time rewrites of {@code etl_target.connect_config}: the legacy {@code jdbcUrl} key and the
 * pre-target-scoped Hive/HDFS settings that used to live in {@code sys_item}.
 *
 * <p>The legacy rows are left untouched so rolling back the application still finds them; only
 * missing keys are filled, so an administrator's edits are never overwritten.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TargetSchemaMigration
{
    private final ObjectMapper objectMapper;
    private final JdbcDriverRegistry driverRegistry;

    /**
     * Some older seeds wrote the JDBC url as {@code jdbcUrl}; every runtime path reads {@code url}.
     * Copy it when {@code url} is missing, keeping the original key for compatibility.
     */
    public void migrateConnectKeys(Connection connection) throws SQLException
    {
        List<TargetRow> rows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
            "select id, connect_config, writer_template_key from public.etl_target");
            ResultSet rs = select.executeQuery())
        {
            while (rs.next())
            {
                rows.add(new TargetRow(rs.getLong("id"), rs.getString("connect_config"),
                    rs.getString("writer_template_key")));
            }
        }
        int migrated = 0;
        for (TargetRow row : rows)
        {
            if (row.connectConfig() == null || row.connectConfig().isBlank())
            {
                continue;
            }
            ObjectNode config;
            try
            {
                JsonNode parsed = objectMapper.readTree(row.connectConfig());
                if (!(parsed instanceof ObjectNode objectNode))
                {
                    continue;
                }
                config = objectNode;
            }
            catch (Exception e)
            {
                log.warn("Skipping connect_config key migration for target {}: invalid JSON", row.id());
                continue;
            }
            JsonNode url = config.get("url");
            JsonNode legacyUrl = config.get("jdbcUrl");
            if (isBlank(url) && legacyUrl != null && legacyUrl.isTextual()
                && !legacyUrl.asText().isBlank())
            {
                config.set("url", legacyUrl);
                try (PreparedStatement update = connection.prepareStatement(
                    "update public.etl_target set connect_config = ? where id = ?"))
                {
                    update.setString(1, objectMapper.writeValueAsString(config));
                    update.setLong(2, row.id());
                    update.executeUpdate();
                }
                catch (Exception e)
                {
                    throw new SQLException("Failed to serialize connect_config for target " + row.id(), e);
                }
                migrated++;
            }
        }
        if (migrated > 0)
        {
            log.info("Copied jdbcUrl into url for {} target endpoint(s)", migrated);
        }
    }

    /**
     * Moves the pre-target-scoped Hive/HDFS settings into each HDFS target's {@code connect_config}.
     *
     * <p>Before multi-target support the HiveServer2 connection, HDFS prefix and storage defaults
     * lived in {@code sys_item} and were shared by every HDFS target. This fills only missing keys,
     * so an administrator who already edited a target keeps their values; the legacy rows are left
     * untouched for one release so rolling back the application still finds them.</p>
     */
    public void migrateHdfsConfig(Connection connection) throws SQLException
    {
        if (!SchemaSupport.exists(connection, "tables", "sys_item", null))
        {
            return;
        }
        Map<String, Object> legacy = legacyHiveConfig(connection);
        if (legacy.isEmpty())
        {
            return;
        }
        List<TargetRow> rows = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                select id, connect_config, writer_template_key
                  from public.etl_target
                 where upper(coalesce(writer_template_key, '')) = 'WH'
                    or upper(coalesce(target_type, '')) in ('HDFS', 'HIVE')
                """);
            ResultSet rs = select.executeQuery())
        {
            while (rs.next())
            {
                rows.add(new TargetRow(rs.getLong("id"), rs.getString("connect_config"),
                    rs.getString("writer_template_key")));
            }
        }
        int migrated = 0;
        for (TargetRow row : rows)
        {
            ObjectNode config;
            if (row.connectConfig() == null || row.connectConfig().isBlank())
            {
                config = JsonNodeFactory.instance.objectNode();
            }
            else
            {
                JsonNode parsed;
                try
                {
                    parsed = objectMapper.readTree(row.connectConfig());
                }
                catch (Exception e)
                {
                    log.warn("Skipping Hive config migration for target {}: invalid connect_config JSON", row.id());
                    continue;
                }
                if (!(parsed instanceof ObjectNode objectNode))
                {
                    log.warn("Skipping Hive config migration for target {}: connect_config is not an object", row.id());
                    continue;
                }
                config = objectNode;
            }
            boolean changed = false;
            for (Map.Entry<String, Object> entry : legacy.entrySet())
            {
                if (isBlank(config.get(entry.getKey())))
                {
                    config.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
                    changed = true;
                }
            }
            String writerKey = row.writerKey();
            if (writerKey == null || writerKey.isBlank())
            {
                writerKey = "wH";
                changed = true;
            }
            if (!changed)
            {
                continue;
            }
            String json;
            try
            {
                json = objectMapper.writeValueAsString(config);
            }
            catch (Exception e)
            {
                throw new SQLException("Failed to serialize Hive target config for target " + row.id(), e);
            }
            try (PreparedStatement update = connection.prepareStatement(
                "update public.etl_target set connect_config = ?, writer_template_key = ? where id = ?"))
            {
                update.setString(1, json);
                update.setString(2, writerKey);
                update.setLong(3, row.id());
                update.executeUpdate();
            }
            migrated++;
        }
        if (migrated > 0)
        {
            log.info("Migrated legacy Hive/HDFS settings into {} target endpoint(s)", migrated);
        }
    }

    private Map<String, Object> legacyHiveConfig(Connection connection) throws SQLException
    {
        Map<String, String> items = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                select item_key, item_value
                  from public.sys_item
                 where dict_code = 1000
                   and item_key in ('HIVE_SERVER2', 'HDFS_PREFIX', 'HDFS_STORAGE_FORMAT',
                                    'HDFS_COMPRESS_FORMAT', 'HDFS_CONFIG')
                """);
            ResultSet rs = select.executeQuery())
        {
            while (rs.next())
            {
                items.put(rs.getString("item_key"), rs.getString("item_value"));
            }
        }
        String writerTemplate = null;
        try (PreparedStatement select = connection.prepareStatement(
            "select item_value from public.sys_item where dict_code = 5001 and item_key = 'wH'");
            ResultSet rs = select.executeQuery())
        {
            if (rs.next())
            {
                writerTemplate = rs.getString("item_value");
            }
        }

        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode hive = parseJson(items.get("HIVE_SERVER2"));
        if (hive != null)
        {
            putText(values, "url", hive, "url");
            putText(values, "username", hive, "username");
            putText(values, "password", hive, "password");
            putText(values, "driverClassName", hive, "driverClassName");
            // Keep an explicit driver path only when the jar is not already in the shared driver
            // directory; after a move there, the isolated registry loads it without a pinned path.
            String legacyDriverPath = text(hive, "driverPath");
            if (legacyDriverPath != null && !legacyDriverPath.isBlank()
                && !driverRegistry.hasSharedDriver(legacyDriverPath))
            {
                values.put("driverPath", legacyDriverPath);
            }
        }
        JsonNode hdfsConfig = parseJson(items.get("HDFS_CONFIG"));
        if (hdfsConfig != null)
        {
            putText(values, "defaultFS", hdfsConfig, "defaultFS");
            putText(values, "hdfsPrefix", hdfsConfig, "hdfsPrefix");
            putText(values, "hdfsSitePath", hdfsConfig, "hdfsSitePath");
            putFlag(values, "enableKerberos", hdfsConfig, "enableKerberos");
            putObject(values, "hadoopConfig", hdfsConfig, "hadoopConfig");
        }
        JsonNode template = parseJson(writerTemplate);
        JsonNode parameter = template == null ? null : template.get("parameter");
        if (parameter instanceof ObjectNode)
        {
            putText(values, "defaultFS", parameter, "defaultFS");
            putText(values, "hdfsSitePath", parameter, "hdfsSitePath");
            putFlag(values, "enableKerberos", parameter, "haveKerberos");
            putObject(values, "hadoopConfig", parameter, "hadoopConfig");
        }
        putText(values, "hdfsPrefix", items.get("HDFS_PREFIX"));
        putText(values, "storageFormat", lower(items.get("HDFS_STORAGE_FORMAT")));
        putText(values, "compressFormat", lower(items.get("HDFS_COMPRESS_FORMAT")));
        Object prefix = values.get("hdfsPrefix");
        if (prefix instanceof String text)
        {
            values.put("hdfsPrefix", normalizePrefix(text));
        }
        values.values().removeIf(value -> value == null
            || (value instanceof String text && text.isBlank())
            || (value instanceof ObjectNode node && node.isEmpty()));
        return values;
    }

    private JsonNode parseJson(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return objectMapper.readTree(value);
        }
        catch (Exception e)
        {
            log.warn("Ignoring unparseable legacy Hive/HDFS config: {}", e.getMessage());
            return null;
        }
    }

    private String text(JsonNode node, String key)
    {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }

    private void putText(Map<String, Object> values, String key, JsonNode node, String nodeKey)
    {
        JsonNode value = node.get(nodeKey);
        if (value != null && !value.isNull() && !value.asText().isBlank())
        {
            values.put(key, value.asText());
        }
    }

    private void putText(Map<String, Object> values, String key, String value)
    {
        if (value != null && !value.isBlank())
        {
            values.put(key, value);
        }
    }

    private void putFlag(Map<String, Object> values, String key, JsonNode node, String nodeKey)
    {
        JsonNode value = node.get(nodeKey);
        if (value != null && value.isBoolean())
        {
            values.put(key, value.asBoolean());
        }
    }

    private void putObject(Map<String, Object> values, String key, JsonNode node, String nodeKey)
    {
        JsonNode value = node.get(nodeKey);
        if (value instanceof ObjectNode object && !object.isEmpty())
        {
            values.put(key, object);
        }
    }

    private String lower(String value)
    {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePrefix(String prefix)
    {
        String value = prefix.trim();
        if (!value.startsWith("/"))
        {
            value = "/" + value;
        }
        while (value.length() > 1 && value.endsWith("/"))
        {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isBlank(JsonNode value)
    {
        return value == null || value.isNull()
            || (value.isTextual() && value.asText().isBlank())
            || (value.isObject() && value.isEmpty());
    }

    private record TargetRow(long id, String connectConfig, String writerKey)
    {
    }
}
