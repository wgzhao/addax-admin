package com.wgzhao.addax.admin.common;

import tools.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.exception.ApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Typed view of {@code etl_table.target_options.doris}.
 *
 * <p>Doris tables carry decisions that cannot be changed after creation: the key model, the key
 * columns, the bucketing and the partition strategy. They are table-level because two source tables
 * on the same cluster can need different models (a re-runnable dimension vs an append-only log).
 * Parsing is explicit so a bad bag fails the preview with a message instead of producing DDL that
 * Doris rejects halfway through a batch.</p>
 */
public record DorisTableOptions(
    String keyModel,
    List<String> keyColumns,
    String distributionType,
    List<String> distributionColumns,
    String buckets,
    Integer replicationNum,
    String partitionMode,
    String partitionColumn,
    int partitionStart,
    int partitionEnd,
    String partitionPrefix)
{
    public static final String AUTO = "AUTO";
    public static final String UNIQUE = "UNIQUE";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String AGGREGATE = "AGGREGATE";
    public static final String HASH = "HASH";
    public static final String RANDOM = "RANDOM";
    public static final String NONE = "NONE";
    public static final String DYNAMIC = "DYNAMIC";

    public static final int DEFAULT_PARTITION_START = -30;
    public static final int DEFAULT_PARTITION_END = 3;
    public static final String DEFAULT_PARTITION_PREFIX = "p";

    public static DorisTableOptions from(JsonNode targetOptions)
    {
        JsonNode section = TargetOptions.section(targetOptions, TargetOptions.DORIS);
        if (section == null)
        {
            return defaults();
        }
        String keyModel = upper(section, "keyModel", AUTO);
        if (!List.of(AUTO, UNIQUE, DUPLICATE, AGGREGATE).contains(keyModel))
        {
            throw new ApiException(400, "Doris 目标表的数据模型只能是 AUTO/UNIQUE/DUPLICATE/AGGREGATE: " + keyModel);
        }
        String distributionType = upper(section, "distributionType", HASH);
        if (!List.of(HASH, RANDOM).contains(distributionType))
        {
            throw new ApiException(400, "Doris 目标表的分桶方式只能是 HASH/RANDOM: " + distributionType);
        }
        String buckets = text(section, "buckets");
        if (buckets == null || buckets.isBlank())
        {
            buckets = AUTO;
        }
        else
        {
            buckets = buckets.trim().toUpperCase(Locale.ROOT);
            if (!AUTO.equals(buckets))
            {
                try
                {
                    int count = Integer.parseInt(buckets);
                    if (count < 1 || count > 1024)
                    {
                        throw new NumberFormatException("out of range");
                    }
                }
                catch (NumberFormatException e)
                {
                    throw new ApiException(400, "Doris 目标表的分桶数必须是 AUTO 或 1-1024 的整数: " + buckets);
                }
            }
        }
        String partitionMode = upper(section, "partitionMode", NONE);
        if (!List.of(NONE, DYNAMIC).contains(partitionMode))
        {
            throw new ApiException(400, "Doris 目标表的分区方式只能是 NONE/DYNAMIC: " + partitionMode);
        }
        String partitionColumn = text(section, "partitionColumn");
        partitionColumn = partitionColumn == null || partitionColumn.isBlank() ? null : partitionColumn.trim();
        int partitionStart = integer(section, "partitionStart", DEFAULT_PARTITION_START);
        int partitionEnd = integer(section, "partitionEnd", DEFAULT_PARTITION_END);
        String partitionPrefix = text(section, "partitionPrefix");
        partitionPrefix = partitionPrefix == null || partitionPrefix.isBlank()
            ? DEFAULT_PARTITION_PREFIX : partitionPrefix.trim();
        if (DYNAMIC.equals(partitionMode))
        {
            if (partitionColumn == null)
            {
                throw new ApiException(400, "Doris 动态分区必须指定分区列（DATE/DATETIME 类型的目标字段）");
            }
            if (partitionStart > -1)
            {
                throw new ApiException(400, "Doris 动态分区保留天数 partitionStart 必须是负数: " + partitionStart);
            }
            if (partitionEnd < 0)
            {
                throw new ApiException(400, "Doris 动态分区预建天数 partitionEnd 不能小于 0: " + partitionEnd);
            }
            if (partitionEnd - partitionStart > 1000)
            {
                throw new ApiException(400, "Doris 动态分区的预建+保留天数过多: " + partitionStart + ".." + partitionEnd);
            }
            if (AUTO.equals(buckets))
            {
                throw new ApiException(400, "Doris 动态分区需要显式分桶数，不能使用 AUTO");
            }
            if (!partitionPrefix.matches("[A-Za-z_][A-Za-z0-9_]*"))
            {
                throw new ApiException(400, "Doris 动态分区前缀只能包含字母、数字、下划线，且不能以数字开头: "
                    + partitionPrefix);
            }
        }
        Integer replicationNum = replicationNum(section);
        return new DorisTableOptions(
            keyModel,
            names(section, "keyColumns"),
            distributionType,
            names(section, "distributionColumns"),
            buckets,
            replicationNum,
            partitionMode,
            partitionColumn,
            partitionStart,
            partitionEnd,
            partitionPrefix);
    }

    public static DorisTableOptions defaults()
    {
        return new DorisTableOptions(
            AUTO, List.of(), HASH, List.of(), AUTO, null, NONE, null,
            DEFAULT_PARTITION_START, DEFAULT_PARTITION_END, DEFAULT_PARTITION_PREFIX);
    }

    private static Integer replicationNum(JsonNode section)
    {
        JsonNode node = section.get("replicationNum");
        if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank()))
        {
            return null;
        }
        int value = node.asInt(0);
        if (value < 1 || value > 10)
        {
            throw new ApiException(400, "Doris 目标表的副本数 replicationNum 必须在 1-10 之间: " + value);
        }
        return value;
    }

    private static String upper(JsonNode section, String key, String fallback)
    {
        String value = text(section, key);
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static int integer(JsonNode section, String key, int fallback)
    {
        JsonNode node = section.get(key);
        if (node == null || node.isNull() || node.asText().isBlank())
        {
            return fallback;
        }
        return node.asInt(fallback);
    }

    /**
     * A comma-separated text is accepted so the detail form can bind a simple text field; the API
     * also stores an array, which is what the UI writes.
     */
    private static List<String> names(JsonNode section, String key)
    {
        JsonNode node = section.get(key);
        if (node == null || node.isNull())
        {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (node.isArray())
        {
            for (JsonNode element : node)
            {
                addName(result, element == null ? null : element.asText());
            }
        }
        else if (node.isTextual())
        {
            for (String part : node.asText().split(","))
            {
                addName(result, part);
            }
        }
        else
        {
            throw new ApiException(400, "Doris 目标表的 " + key + " 必须是数组或逗号分隔的字符串");
        }
        return List.copyOf(result);
    }

    private static void addName(List<String> result, String value)
    {
        if (value == null)
        {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty() && result.stream().noneMatch(name -> name.equalsIgnoreCase(trimmed)))
        {
            result.add(trimmed);
        }
    }

    private static String text(JsonNode section, String key)
    {
        JsonNode value = section.get(key);
        return value == null || value.isNull() ? null : value.asText();
    }
}
