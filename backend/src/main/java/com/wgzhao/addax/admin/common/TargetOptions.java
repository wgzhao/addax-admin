package com.wgzhao.addax.admin.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Typed access to {@code etl_table.target_options}.
 *
 * <p>Target-specific attributes live in a namespaced bag (hdfs / rdbms / ...) instead of fixed columns
 * on {@code etl_table}, so a new target never grows the table and Hive-only concepts stop being table
 * level assumptions.</p>
 *
 * <p>The accessors are deliberately literal: an absent section or key returns {@code null} rather than a
 * default. A default here would be target-blind — {@code partName} would keep reporting "logdate" for
 * every RDBMS row, the very bug this bag removes. Each adapter owns the defaults it actually needs:
 * the HDFS adapter falls back to the global HDFS settings for storage/compression, and a missing
 * partition column simply means the table is not partitioned.</p>
 */
public final class TargetOptions
{
    public static final String HDFS = "hdfs";
    public static final String RDBMS = "rdbms";

    private TargetOptions()
    {
    }

    /**
     * Section of the bag, or null when this target family has no attributes on the row.
     */
    public static JsonNode section(JsonNode root, String section)
    {
        if (root == null || !root.isObject())
        {
            return null;
        }
        JsonNode node = root.get(section);
        return node != null && node.isObject() ? node : null;
    }

    private static String text(JsonNode section, String key)
    {
        if (section == null)
        {
            return null;
        }
        JsonNode value = section.get(key);
        if (value == null || value.isNull())
        {
            return null;
        }
        return value.asText();
    }

    private static boolean flag(JsonNode section, String key, boolean fallback)
    {
        if (section == null)
        {
            return fallback;
        }
        JsonNode value = section.get(key);
        if (value == null || value.isNull())
        {
            return fallback;
        }
        return value.asBoolean(fallback);
    }

    // ---------------------------------------------------------------- hdfs

    public static String hdfsPartKind(JsonNode root)
    {
        return text(section(root, HDFS), "partKind");
    }

    public static String hdfsPartName(JsonNode root)
    {
        return text(section(root, HDFS), "partName");
    }

    public static String hdfsPartFormat(JsonNode root)
    {
        return text(section(root, HDFS), "partFormat");
    }

    public static String hdfsStorageFormat(JsonNode root)
    {
        return text(section(root, HDFS), "storageFormat");
    }

    public static String hdfsCompressFormat(JsonNode root)
    {
        return text(section(root, HDFS), "compressFormat");
    }

    /**
     * Partition creation is runtime preparation, separate from table/column schema ownership.
     */
    public static String hdfsPartitionCreationMode(JsonNode root)
    {
        String mode = text(section(root, HDFS), "partitionCreationMode");
        return mode == null || mode.isBlank() ? "PLATFORM" : mode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Binds discovered target partition keys to runtime values. Legacy partName/partFormat is accepted
     * as one business-date binding so existing rows keep producing the same HDFS path.
     */
    public static List<HdfsPartitionBinding> hdfsPartitionBindings(JsonNode root)
    {
        JsonNode section = section(root, HDFS);
        if (section != null)
        {
            JsonNode array = section.get("partitionBindings");
            if (array != null && array.isArray())
            {
                List<HdfsPartitionBinding> result = new ArrayList<>();
                for (JsonNode node : array)
                {
                    String name = text(node, "name");
                    String valueType = text(node, "valueType");
                    if (name == null || name.isBlank() || valueType == null || valueType.isBlank())
                    {
                        continue;
                    }
                    result.add(new HdfsPartitionBinding(
                        name.trim(),
                        valueType.trim().toUpperCase(Locale.ROOT),
                        text(node, "value"),
                        text(node, "format")));
                }
                if (!result.isEmpty())
                {
                    return List.copyOf(result);
                }
            }
        }
        String legacyName = hdfsPartName(root);
        if (legacyName == null || legacyName.isBlank())
        {
            return List.of();
        }
        String format = hdfsPartFormat(root);
        return List.of(new HdfsPartitionBinding(
            legacyName.trim(),
            "BIZ_DATE",
            null,
            format == null || format.isBlank() ? "yyyyMMdd" : format.trim()));
    }

    // --------------------------------------------------------------- rdbms

    /**
     * Business date column carried into the target for snapshot collection, null when unused.
     */
    public static String rdbmsBusinessDateColumn(JsonNode root)
    {
        return text(section(root, RDBMS), "businessDateColumn");
    }

    /**
     * A row that never configured the key means "create the primary key": the option only exists to turn
     * it off, so an absent key cannot be read as "off".
     */
    public static boolean rdbmsCreatePrimaryKey(JsonNode root)
    {
        return flag(section(root, RDBMS), "createPrimaryKey", true);
    }

    public record HdfsPartitionBinding(
        String name,
        String valueType,
        String value,
        String format
    )
    {
    }
}
