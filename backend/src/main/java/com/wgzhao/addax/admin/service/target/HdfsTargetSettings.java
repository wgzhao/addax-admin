package com.wgzhao.addax.admin.service.target;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlTarget;

import java.util.Locale;
import java.util.Set;

/**
 * Typed view of an HDFS/Hive target's {@code etl_target.connect_config}.
 *
 * <p>Connection and HDFS writer defaults belong to the target endpoint, not to a global system
 * setting: two HDFS targets may point at different HiveServer2 instances or write to different
 * prefixes. The record is parsed leniently for runtime use (a missing {@code defaultFS} is fine for
 * DDL) and strictly when a target is saved, so an unusable writer configuration is rejected at
 * configuration time instead of at 03:00 on the first collection run.</p>
 */
public record HdfsTargetSettings(
    String url,
    String username,
    String password,
    String driverClassName,
    String driverPath,
    String defaultFS,
    String hdfsPrefix,
    String hdfsSitePath,
    boolean enableKerberos,
    JsonNode hadoopConfig,
    String storageFormat,
    String compressFormat)
{
    public static final String DEFAULT_DRIVER_CLASS = "org.apache.hive.jdbc.HiveDriver";
    public static final String DEFAULT_HDFS_PREFIX = "/ods";
    public static final String DEFAULT_STORAGE_FORMAT = "orc";
    public static final String DEFAULT_COMPRESS_FORMAT = "snappy";
    public static final Set<String> STORAGE_FORMATS = Set.of("orc", "parquet", "avro", "textfile");
    public static final Set<String> COMPRESS_FORMATS = Set.of("lz4", "snappy", "gzip", "zstd", "zlib");

    private static final String HIVE_URL_PREFIX = "jdbc:hive2://";

    public static HdfsTargetSettings from(EtlTarget target, ObjectMapper objectMapper)
    {
        if (target == null)
        {
            throw new ApiException(400, "目标端不存在");
        }
        JsonNode root;
        String config = target.getConnectConfig();
        if (config == null || config.isBlank())
        {
            root = JsonNodeFactory.instance.objectNode();
        }
        else
        {
            try
            {
                root = objectMapper.readTree(config);
            }
            catch (Exception e)
            {
                throw new ApiException(400, "目标端连接配置不是有效 JSON: " + target.getName());
            }
        }
        if (root == null || !root.isObject())
        {
            throw new ApiException(400, "目标端连接配置必须是 JSON 对象: " + target.getName());
        }

        JsonNode hadoopConfig = root.get("hadoopConfig");
        if (hadoopConfig == null || hadoopConfig.isNull())
        {
            hadoopConfig = JsonNodeFactory.instance.objectNode();
        }
        else if (!hadoopConfig.isObject())
        {
            throw new ApiException(400, "hadoopConfig 必须是 JSON 对象: " + target.getName());
        }

        String driverClassName = text(root, "driverClassName");
        return new HdfsTargetSettings(
            text(root, "url"),
            text(root, "username"),
            text(root, "password"),
            driverClassName.isBlank() ? DEFAULT_DRIVER_CLASS : driverClassName.trim(),
            text(root, "driverPath").trim(),
            text(root, "defaultFS"),
            normalizePrefix(text(root, "hdfsPrefix")),
            text(root, "hdfsSitePath"),
            flag(root, "enableKerberos"),
            hadoopConfig,
            normalizeFormat(text(root, "storageFormat"), DEFAULT_STORAGE_FORMAT,
                STORAGE_FORMATS, "storageFormat", target),
            normalizeFormat(text(root, "compressFormat"), DEFAULT_COMPRESS_FORMAT,
                COMPRESS_FORMATS, "compressFormat", target));
    }

    /**
     * Reject a target that cannot serve a writer job. Called from the target save path only; runtime
     * reads stay lenient so an existing deployment can still run DDL while an administrator fixes the
     * target.
     */
    public void validateForSave(EtlTarget target)
    {
        String name = target == null ? "" : target.getName();
        if (url == null || url.isBlank())
        {
            throw new ApiException(400, "HDFS 目标端必须配置 HiveServer2 JDBC 地址: " + name);
        }
        if (!url.toLowerCase(Locale.ROOT).startsWith(HIVE_URL_PREFIX))
        {
            throw new ApiException(400, "HiveServer2 JDBC 地址必须以 jdbc:hive2:// 开头: " + name);
        }
        if (defaultFS == null || defaultFS.isBlank())
        {
            throw new ApiException(400, "HDFS 目标端必须配置 defaultFS（例如 hdfs://nameservice）: " + name);
        }
    }

    private static String text(JsonNode node, String key)
    {
        JsonNode value = node.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static boolean flag(JsonNode node, String key)
    {
        JsonNode value = node.get(key);
        return value != null && value.asBoolean(false);
    }

    private static String normalizePrefix(String prefix)
    {
        String value = prefix == null ? "" : prefix.trim();
        if (value.isEmpty())
        {
            return DEFAULT_HDFS_PREFIX;
        }
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

    private static String normalizeFormat(String value, String fallback, Set<String> allowed,
        String key, EtlTarget target)
    {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty())
        {
            return fallback;
        }
        if (!allowed.contains(normalized))
        {
            throw new ApiException(400, key + " 不支持 '" + value + "'，可选: "
                + String.join(", ", allowed.stream().sorted().toList())
                + (target == null ? "" : " (" + target.getName() + ")"));
        }
        return normalized;
    }
}
