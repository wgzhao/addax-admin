package com.wgzhao.addax.admin.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * Typed access to {@code etl_table.target_options}.
 *
 * <p>Target-specific attributes live in a namespaced bag (hdfs / rdbms / ...) instead of fixed columns
 * on {@code etl_table}, so a new target never grows the table and Hive-only concepts stop being table
 * level assumptions. Absent keys fall back to the historical defaults so a row written before the
 * migration keeps behaving the way it used to.</p>
 */
public final class TargetOptions
{
    public static final String HDFS = "hdfs";
    public static final String RDBMS = "rdbms";

    public static final String DEFAULT_PART_KIND = "D";
    public static final String DEFAULT_PART_NAME = "logdate";
    public static final String DEFAULT_PART_FORMAT = Constants.DEFAULT_PART_FORMAT;
    public static final String DEFAULT_STORAGE_FORMAT = "orc";
    public static final String DEFAULT_COMPRESS_FORMAT = "snappy";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private TargetOptions()
    {
    }

    /**
     * The Hive storage section, or an empty object so callers can read the defaults. Never returns null.
     */
    public static JsonNode section(JsonNode root, String section)
    {
        if (root == null || !root.isObject())
        {
            return NODES.objectNode();
        }
        JsonNode node = root.get(section);
        return node != null && node.isObject() ? node : NODES.objectNode();
    }

    private static String text(JsonNode node, String key, String fallback)
    {
        if (node == null)
        {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull())
        {
            return fallback;
        }
        return value.asText();
    }

    private static boolean flag(JsonNode node, String key, boolean fallback)
    {
        if (node == null)
        {
            return fallback;
        }
        JsonNode value = node.get(key);
        if (value == null || value.isNull())
        {
            return fallback;
        }
        return value.asBoolean(fallback);
    }

    // ---------------------------------------------------------------- hdfs

    public static String hdfsPartKind(JsonNode root)
    {
        return text(section(root, HDFS), "partKind", DEFAULT_PART_KIND);
    }

    public static String hdfsPartName(JsonNode root)
    {
        return text(section(root, HDFS), "partName", DEFAULT_PART_NAME);
    }

    public static String hdfsPartFormat(JsonNode root)
    {
        return text(section(root, HDFS), "partFormat", DEFAULT_PART_FORMAT);
    }

    public static String hdfsStorageFormat(JsonNode root)
    {
        return text(section(root, HDFS), "storageFormat", DEFAULT_STORAGE_FORMAT);
    }

    public static String hdfsCompressFormat(JsonNode root)
    {
        return text(section(root, HDFS), "compressFormat", DEFAULT_COMPRESS_FORMAT);
    }

    // --------------------------------------------------------------- rdbms

    /**
     * Business date column carried into the target for snapshot collection, empty when unused.
     */
    public static String rdbmsBusinessDateColumn(JsonNode root)
    {
        return text(section(root, RDBMS), "businessDateColumn", "");
    }

    public static boolean rdbmsCreatePrimaryKey(JsonNode root)
    {
        return flag(section(root, RDBMS), "createPrimaryKey", true);
    }
}
