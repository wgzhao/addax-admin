package com.wgzhao.addax.admin.service.target;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Typed view of a Doris target's {@code etl_target.connect_config}.
 *
 * <p>Doris exposes two independent endpoints: the MySQL-protocol FE port used for JDBC metadata and
 * the HTTP endpoint used by {@code doriswriter} for Stream Load. Both belong to the target endpoint;
 * they are not interchangeable, and a missing {@code loadUrl} only fails once the writer flushes.
 * Parsing is strict at save time so an unusable writer configuration is rejected at configuration
 * time instead of at 03:00 on the first collection run.</p>
 */
public record DorisTargetSettings(
    String url,
    String username,
    String password,
    List<String> loadUrl,
    long batchSize,
    long flushInterval,
    int flushQueueLength,
    JsonNode loadProps)
{
    public static final long DEFAULT_BATCH_SIZE = 2048;
    public static final long DEFAULT_FLUSH_INTERVAL = 3000;
    public static final int DEFAULT_FLUSH_QUEUE_LENGTH = 1;

    private static final String MYSQL_URL_PREFIX = "jdbc:mysql://";

    public static DorisTargetSettings from(EtlTarget target, ObjectMapper objectMapper)
    {
        if (target == null)
        {
            throw new ApiException(400, "目标端不存在");
        }
        JsonNode root = readConfig(target, objectMapper);
        JsonNode loadProps = root.get("loadProps");
        if (loadProps == null || loadProps.isNull())
        {
            loadProps = JsonNodeFactory.instance.objectNode();
        }
        else if (!loadProps.isObject())
        {
            throw new ApiException(400, "Doris 目标端的 loadProps 必须是 JSON 对象: " + target.getName());
        }
        return new DorisTargetSettings(
            text(root, "url"),
            text(root, "username"),
            text(root, "password"),
            readLoadUrl(target, root.get("loadUrl")),
            positiveLong(target, root.get("batchSize"), DEFAULT_BATCH_SIZE, "batchSize"),
            positiveLong(target, root.get("flushInterval"), DEFAULT_FLUSH_INTERVAL, "flushInterval"),
            positiveInt(target, root.get("flushQueueLength"), DEFAULT_FLUSH_QUEUE_LENGTH, "flushQueueLength"),
            loadProps);
    }

    /**
     * Reject a target that cannot serve a writer job. Used by the target save path and by job
     * generation, so a hand-edited config cannot reach the writer half-valid.
     */
    public void requireJdbcConfigured(String targetName)
    {
        String name = targetName == null ? "" : targetName;
        if (url == null || url.isBlank())
        {
            throw new ApiException(400, "Doris 目标端必须配置 FE 的 JDBC 地址: " + name);
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith(MYSQL_URL_PREFIX))
        {
            throw new ApiException(400, "Doris 目标端的 JDBC 地址必须以 jdbc:mysql:// 开头（FE 的 MySQL 协议端口，通常为 9030）: " + name);
        }
    }

    /**
     * The JDBC URL without its database path. Metadata and DDL work is database-agnostic (every
     * query names information_schema or a qualified table), and a managed target may point at a
     * database that does not exist yet: connecting to it directly would fail with "Unknown database"
     * before CREATE DATABASE IF NOT EXISTS could run. The writer keeps the configured URL unchanged.
     */
    public String serverUrl()
    {
        if (url == null || url.isBlank())
        {
            return "";
        }
        int prefix = MYSQL_URL_PREFIX.length();
        int slash = url.indexOf('/', prefix);
        if (slash < 0)
        {
            return url;
        }
        int query = url.indexOf('?', slash);
        return query < 0 ? url.substring(0, slash) : url.substring(0, slash) + url.substring(query);
    }

    public void requireWritable(String targetName)
    {
        requireJdbcConfigured(targetName);
        String name = targetName == null ? "" : targetName;
        if (loadUrl == null || loadUrl.isEmpty())
        {
            throw new ApiException(400, "Doris 目标端必须配置 loadUrl（Stream Load 的 FE/BE HTTP 端点，通常为 8030）: " + name);
        }
        for (String endpoint : loadUrl)
        {
            validateEndpoint(endpoint, name);
        }
    }

    private static JsonNode readConfig(EtlTarget target, ObjectMapper objectMapper)
    {
        String config = target.getConnectConfig();
        if (config == null || config.isBlank())
        {
            throw new ApiException(400, "Doris 目标端连接配置不能为空: " + target.getName());
        }
        try
        {
            JsonNode root = objectMapper.readTree(config);
            if (root == null || !root.isObject())
            {
                throw new ApiException(400, "Doris 目标端连接配置必须是 JSON 对象: " + target.getName());
            }
            return root;
        }
        catch (ApiException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ApiException(400, "Doris 目标端连接配置不是有效 JSON: " + target.getName());
        }
    }

    private static List<String> readLoadUrl(EtlTarget target, JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return List.of();
        }
        List<String> endpoints = new ArrayList<>();
        if (node.isArray())
        {
            for (JsonNode element : node)
            {
                if (element != null && element.isTextual())
                {
                    addEndpoint(endpoints, element.asText());
                }
            }
        }
        else if (node.isTextual())
        {
            for (String part : node.asText().split("[,;\\n]"))
            {
                addEndpoint(endpoints, part);
            }
        }
        else
        {
            throw new ApiException(400, "Doris 目标端的 loadUrl 必须是数组或逗号分隔的字符串: "
                + target.getName());
        }
        return List.copyOf(endpoints);
    }

    private static void addEndpoint(List<String> endpoints, String value)
    {
        if (value == null)
        {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty())
        {
            endpoints.add(trimmed);
        }
    }

    /**
     * The plugin accepts {@code host:port}; anything else either fails when the batch is flushed or
     * is silently unusable. Validate now so the operator sees the bad endpoint.
     */
    private static void validateEndpoint(String endpoint, String name)
    {
        int colon = endpoint.lastIndexOf(':');
        if (colon <= 0 || colon == endpoint.length() - 1)
        {
            throw new ApiException(400, "loadUrl 端点 '" + endpoint
                + "' 格式不正确，应为 host:port（" + name + "）");
        }
        String host = endpoint.substring(0, colon).trim();
        String port = endpoint.substring(colon + 1).trim();
        if (host.isEmpty() || host.startsWith("http://") || host.startsWith("https://"))
        {
            throw new ApiException(400, "loadUrl 端点 '" + endpoint
                + "' 只写 host:port，不要带协议前缀（" + name + "）");
        }
        try
        {
            int value = Integer.parseInt(port);
            if (value < 1 || value > 65535)
            {
                throw new NumberFormatException("out of range");
            }
        }
        catch (NumberFormatException e)
        {
            throw new ApiException(400, "loadUrl 端点 '" + endpoint + "' 的端口不是有效端口号（" + name + "）");
        }
    }

    private static long positiveLong(EtlTarget target, JsonNode node, long fallback, String key)
    {
        if (node == null || node.isNull())
        {
            return fallback;
        }
        long value = node.asLong(0);
        if (value <= 0)
        {
            throw new ApiException(400, "Doris 目标端的 " + key + " 必须大于 0: " + target.getName());
        }
        return value;
    }

    private static int positiveInt(EtlTarget target, JsonNode node, int fallback, String key)
    {
        if (node == null || node.isNull())
        {
            return fallback;
        }
        int value = node.asInt(0);
        if (value <= 0)
        {
            throw new ApiException(400, "Doris 目标端的 " + key + " 必须大于 0: " + target.getName());
        }
        return value;
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
