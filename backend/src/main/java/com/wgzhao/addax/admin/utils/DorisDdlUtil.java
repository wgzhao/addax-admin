package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.common.DorisTableOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Doris-specific type rendering and DDL generation.
 *
 * <p>Doris is not rendered through {@link RdbmsDdlUtil}: the MySQL-compatible protocol does not make
 * its DDL MySQL. A Doris table always names a key model, key columns must be the leading columns, and
 * several source types (TEXT/BLOB/TIME) have no Doris equivalent. This class therefore renders types
 * for Doris and fails explicitly for a column it cannot represent, instead of emitting DDL that Doris
 * rejects after the table was already declared managed.</p>
 */
public final class DorisDdlUtil
{
    private DorisDdlUtil()
    {
    }

    /**
     * A column as the planner sees it: the rendered Doris type, whether the source marks it as a key,
     * and the source nullability (used only to warn about keys, never to relax them).
     */
    public record PlannedColumn(
        String name,
        String type,
        boolean nullable,
        boolean sourceKey,
        String comment,
        String role)
    {
    }

    public record TablePlan(
        String ddl,
        String keyModel,
        List<String> keyColumns,
        List<PlannedColumn> orderedColumns,
        List<String> warnings)
    {
    }

    /**
     * A planning failure tied to an optional column, so the plan preview can report it as a finding
     * and the refresh can record which column made the model impossible.
     */
    public static class DorisDdlException
        extends RuntimeException
    {
        private final String column;

        public DorisDdlException(String column, String message)
        {
            super(message);
            this.column = column;
        }

        public String column()
        {
            return column;
        }
    }

    private enum Kind
    {
        STRING, TEXT, INTEGER, BIGINT, SMALLINT, TINYINT, BOOLEAN,
        DECIMAL, FLOAT, DOUBLE, DATE, TIME, TIMESTAMP, BINARY, JSON, RAW
    }

    /**
     * Render a source type as the Doris spelling. Lengths above the safe varchar bound become STRING;
     * unsigned integers are widened because Doris has no unsigned types.
     */
    public static String renderType(String sourceType, int length, int precision, int scale)
    {
        String normalized = normalize(sourceType);
        boolean unsigned = normalized.contains(" unsigned");
        Kind kind = kindOf(normalized);
        switch (kind)
        {
            case STRING:
            {
                int n = length > 0 ? length : 255;
                // 16383 four-byte characters fit in the 65533-byte VARCHAR limit; anything larger
                // becomes STRING (Doris' unbounded text type).
                return n > 16383 ? "string" : "varchar(" + n + ")";
            }
            case TEXT:
                return "string";
            case INTEGER:
                return unsigned ? "bigint" : "int";
            case BIGINT:
                return unsigned ? "largeint" : "bigint";
            case SMALLINT:
                return unsigned ? "int" : "smallint";
            case TINYINT:
                return unsigned ? "smallint" : "tinyint";
            case BOOLEAN:
                return "boolean";
            case DECIMAL:
            {
                int p = precision > 0 ? precision : 10;
                if (p > 38)
                {
                    throw new DorisDdlException(null, "Doris DECIMAL 精度最大 38，当前为 " + p);
                }
                int s = Math.max(scale, 0);
                if (s > p)
                {
                    s = p;
                }
                return "decimal(" + p + "," + s + ")";
            }
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case DATE:
                return "date";
            case TIME:
                throw new DorisDdlException(null,
                    "Doris 没有 TIME 类型，源字段 " + sourceType + " 请映射为其他类型或改用外部表");
            case TIMESTAMP:
            {
                // Doris DATETIME(p) keeps sub-second precision; DATETIME without it truncates.
                int fractional = Math.max(0, Math.min(scale, 6));
                return fractional > 0 ? "datetime(" + fractional + ")" : "datetime";
            }
            case BINARY:
                throw new DorisDdlException(null,
                    "Doris 不支持二进制类型（" + sourceType + "），请改为外部表或映射为字符串");
            case JSON:
                return "json";
            case RAW:
            default:
                throw new DorisDdlException(null,
                    "无法把源类型 '" + sourceType + "' 映射为 Doris 类型，请显式指定目标字段类型或改用外部表");
        }
    }

    /**
     * Accept a type another adapter already rendered (MongoDB/Elasticsearch) when it is a Doris
     * type, and normalize the aliases those mappers emit. Returns null for anything that must be
     * rendered from the logical source type instead — a MySQL display width or a TEXT alias would
     * otherwise leak into the DDL.
     */
    public static String normalizeProvidedType(String provided)
    {
        if (provided == null || provided.isBlank())
        {
            return null;
        }
        String value = provided.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!value.matches("[a-z][a-z0-9_ ]*(\\(\\s*\\d+(\\s*,\\s*\\d+)?\\s*\\))?"))
        {
            return null;
        }
        String base = baseType(value);
        String suffix = value.length() > base.length() ? value.substring(base.length()) : "";
        switch (base)
        {
            case "varchar":
            case "char":
            case "decimal":
            case "json":
            case "largeint":
                return value;
            case "datetime":
                return value;
            case "timestamp":
                return "datetime" + suffix;
            case "text":
            case "tinytext":
            case "mediumtext":
            case "longtext":
            case "clob":
            case "string":
                return "string";
            case "int":
            case "integer":
            case "bigint":
            case "smallint":
            case "tinyint":
            case "boolean":
            case "bool":
            case "double":
            case "float":
            case "real":
            case "date":
                break;
            default:
                return null;
        }
        String canonical = switch (base)
        {
            case "integer" -> "int";
            case "bool" -> "boolean";
            case "real" -> "float";
            default -> base;
        };
        // MySQL reports display widths (int(11), tinyint(1)); Doris rejects them.
        return canonical + ("decimal".equals(canonical) || "varchar".equals(canonical)
            || "char".equals(canonical) ? suffix : "");
    }

    /**
     * Plan a managed Doris table: validate the model, order the key columns first and render the DDL.
     * The same function serves the preview and the refresh so the shown DDL is the executed DDL.
     */
    public static TablePlan planTable(String db, String table, List<PlannedColumn> input,
        DorisTableOptions options, String tableComment)
    {
        if (input == null || input.isEmpty())
        {
            throw new DorisDdlException(null, "没有可建表的字段，无法生成 Doris 表结构");
        }
        List<String> warnings = new ArrayList<>();
        Map<String, PlannedColumn> byName = new LinkedHashMap<>();
        for (PlannedColumn column : input)
        {
            String name = column.name() == null ? null : column.name().trim();
            if (name == null || name.isBlank())
            {
                throw new DorisDdlException(null, "Doris 目标字段名不能为空");
            }
            if (byName.putIfAbsent(name.toLowerCase(Locale.ROOT), column) != null)
            {
                throw new DorisDdlException(name, "Doris 目标字段名重复: " + name);
            }
        }

        String partitionColumn = null;
        if (DorisTableOptions.DYNAMIC.equals(options.partitionMode()))
        {
            partitionColumn = resolveName(byName, options.partitionColumn(), "分区列");
            String base = baseType(byName.get(partitionColumn.toLowerCase(Locale.ROOT)).type());
            if (!"date".equals(base) && !"datetime".equals(base))
            {
                throw new DorisDdlException(byName.get(partitionColumn.toLowerCase(Locale.ROOT)).name(),
                    "Doris 动态分区列必须是 DATE/DATETIME 类型，当前为 "
                        + byName.get(partitionColumn.toLowerCase(Locale.ROOT)).type());
            }
        }

        if (DorisTableOptions.AGGREGATE.equals(options.keyModel()))
        {
            throw new DorisDdlException(null,
                "Doris AGGREGATE 模型需要为每个值列指定聚合函数（sum/replace/max 等），平台尚无该信息；"
                    + "请使用 UNIQUE/DUPLICATE，或改用外部表由 DBA 定义");
        }

        List<String> keyColumns = resolveKeyColumns(byName, options, partitionColumn);
        boolean sourceKeys = hasSourceKey(byName);
        boolean explicitKeys = !options.keyColumns().isEmpty();
        String keyModel = options.keyModel();
        if (DorisTableOptions.AUTO.equals(keyModel))
        {
            // A source primary key (or an explicit key list) is the signal that a re-run should update
            // instead of append. A partition-only key does not imply uniqueness: that would collapse a
            // whole day's rows into one.
            keyModel = sourceKeys || explicitKeys
                ? DorisTableOptions.UNIQUE : DorisTableOptions.DUPLICATE;
        }
        if (DorisTableOptions.UNIQUE.equals(keyModel)
            && (keyColumns.isEmpty() || (!sourceKeys && !explicitKeys)))
        {
            throw new DorisDdlException(null,
                "Doris UNIQUE 模型需要源端主键或显式键列；当前源没有可用主键，请改用 DUPLICATE 模型或指定键列");
        }
        if (keyColumns.isEmpty())
        {
            // DUPLICATE tables still sort by a leading key; the first non-nullable column is the safest
            // default and matches Doris' own behavior when no KEY is declared.
            PlannedColumn fallback = input.stream()
                .filter(column -> !column.nullable())
                .findFirst()
                .orElse(input.get(0));
            keyColumns = List.of(fallback.name());
            warnings.add("未指定键列且源端无主键，Doris DUPLICATE 表使用首个非空列 "
                + fallback.name() + " 作为键列");
        }
        final String partitionKey = partitionColumn;
        List<String> currentKeys = keyColumns;
        if (partitionKey != null
            && currentKeys.stream().noneMatch(name -> name.equalsIgnoreCase(partitionKey)))
        {
            List<String> withPartition = new ArrayList<>();
            withPartition.add(partitionKey);
            withPartition.addAll(keyColumns);
            keyColumns = withPartition;
        }
        keyColumns = List.copyOf(keyColumns);

        for (String key : keyColumns)
        {
            PlannedColumn column = byName.get(key.toLowerCase(Locale.ROOT));
            if (!isKeyType(column.type()))
            {
                throw new DorisDdlException(column.name(),
                    "Doris 键列 " + column.name() + " 的类型 " + column.type()
                        + " 不能作为键列；键列只支持数值、日期/时间和 CHAR/VARCHAR");
            }
            if (column.nullable())
            {
                warnings.add("键列 " + column.name()
                    + " 在源端可空，Doris 键列按 NOT NULL 创建；源数据出现 NULL 会导致导入失败");
            }
        }

        String distributionType = options.distributionType();
        List<String> distributionColumns;
        if (DorisTableOptions.RANDOM.equals(distributionType))
        {
            if (!DorisTableOptions.DUPLICATE.equals(keyModel))
            {
                throw new DorisDdlException(null, "Doris RANDOM 分桶只支持 DUPLICATE 模型");
            }
            if (!options.distributionColumns().isEmpty())
            {
                throw new DorisDdlException(null, "Doris RANDOM 分桶不能指定分桶列");
            }
            distributionColumns = List.of();
        }
        else
        {
            distributionColumns = options.distributionColumns().isEmpty()
                ? keyColumns
                : options.distributionColumns().stream()
                    .map(name -> resolveName(byName, name, "分桶列"))
                    .toList();
            for (String name : distributionColumns)
            {
                if (keyColumns.stream().noneMatch(key -> key.equalsIgnoreCase(name)))
                {
                    throw new DorisDdlException(name,
                        "Doris 分桶列 " + name + " 必须是键列（当前键列: " + String.join(", ", keyColumns) + "）");
                }
            }
        }

        String buckets = options.buckets();
        if (DorisTableOptions.RANDOM.equals(distributionType) && DorisTableOptions.AUTO.equals(buckets))
        {
            // Doris only supports AUTO buckets for HASH distribution.
            buckets = "10";
            warnings.add("RANDOM 分桶不支持 AUTO，已按 10 个分桶创建");
        }

        Set<String> keySet = new LinkedHashSet<>();
        keyColumns.forEach(key -> keySet.add(key.toLowerCase(Locale.ROOT)));
        List<PlannedColumn> ordered = new ArrayList<>();
        for (String key : keyColumns)
        {
            ordered.add(byName.get(key.toLowerCase(Locale.ROOT)));
        }
        for (PlannedColumn column : input)
        {
            if (!keySet.contains(column.name().toLowerCase(Locale.ROOT)))
            {
                ordered.add(column);
            }
        }

        List<String> definitions = new ArrayList<>();
        for (PlannedColumn column : ordered)
        {
            boolean key = keySet.contains(column.name().toLowerCase(Locale.ROOT));
            definitions.add(columnDefinition(column.name(), column.type(), key, column.comment()));
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(tableRef(db, table)).append(" (\n")
            .append(String.join(",\n", definitions))
            .append("\n) ENGINE=OLAP\n")
            .append(keyModel).append(" KEY(").append(quotedList(keyColumns)).append(')');
        if (tableComment != null && !tableComment.isBlank())
        {
            ddl.append("\nCOMMENT '").append(escapeLiteral(tableComment)).append('\'');
        }
        if (partitionColumn != null)
        {
            ddl.append("\nPARTITION BY RANGE(").append(quote(partitionColumn)).append(") ()");
        }
        ddl.append("\nDISTRIBUTED BY ");
        if (DorisTableOptions.RANDOM.equals(distributionType))
        {
            ddl.append("RANDOM");
        }
        else
        {
            ddl.append("HASH(").append(quotedList(distributionColumns)).append(')');
        }
        ddl.append(" BUCKETS ").append(buckets);

        List<String> properties = new ArrayList<>();
        if (options.replicationNum() != null)
        {
            properties.add("\"replication_num\" = \"" + options.replicationNum() + '"');
        }
        if (partitionColumn != null)
        {
            properties.add("\"dynamic_partition.enable\" = \"true\"");
            properties.add("\"dynamic_partition.time_unit\" = \"DAY\"");
            properties.add("\"dynamic_partition.start\" = \"" + options.partitionStart() + '"');
            // Without history partitions the initial table only owns today..+end, so a T+1 load of
            // yesterday's snapshot fails until the scheduler adds the partition.
            properties.add("\"dynamic_partition.create_history_partition\" = \"true\"");
            properties.add("\"dynamic_partition.end\" = \"" + options.partitionEnd() + '"');
            properties.add("\"dynamic_partition.prefix\" = \"" + options.partitionPrefix() + '"');
            properties.add("\"dynamic_partition.buckets\" = \"" + buckets + '"');
        }
        if (!properties.isEmpty())
        {
            ddl.append("\nPROPERTIES (\n  ").append(String.join(",\n  ", properties)).append("\n)");
        }

        return new TablePlan(ddl.toString(), keyModel, keyColumns, List.copyOf(ordered),
            List.copyOf(warnings));
    }

    public static String columnDefinition(String name, String type, boolean notNull, String comment)
    {
        StringBuilder sb = new StringBuilder(quote(name)).append(' ').append(type);
        if (notNull)
        {
            sb.append(" NOT NULL");
        }
        if (comment != null && !comment.isBlank())
        {
            sb.append(" COMMENT '").append(escapeLiteral(comment)).append('\'');
        }
        return sb.toString();
    }

    public static String addColumn(String tableRef, String columnDef)
    {
        return "ALTER TABLE " + tableRef + " ADD COLUMN " + columnDef;
    }

    /**
     * Doris keeps the other column attributes when only the type changes; the caller must have
     * verified that this is a non-key column.
     */
    public static String modifyColumn(String tableRef, String name, String type)
    {
        return "ALTER TABLE " + tableRef + " MODIFY COLUMN " + quote(name) + " " + type;
    }

    public static String tableRef(String db, String table)
    {
        String quotedTable = quote(table);
        return db == null || db.isBlank() ? quotedTable : quote(db) + "." + quotedTable;
    }

    public static String quote(String name)
    {
        return "`" + (name == null ? "" : name.replace("`", "``")) + "`";
    }

    /**
     * Doris key columns accept only ordered scalar types. FLOAT/DOUBLE, STRING, JSON and binary
     * columns cannot be keys, so a source primary key of such a type fails instead of creating a
     * table whose key Doris would silently reject.
     */
    public static boolean isKeyType(String type)
    {
        switch (baseType(type))
        {
            case "boolean":
            case "tinyint":
            case "smallint":
            case "int":
            case "bigint":
            case "largeint":
            case "decimal":
            case "date":
            case "datetime":
            case "char":
            case "varchar":
                return true;
            default:
                return false;
        }
    }

    public static String baseType(String type)
    {
        if (type == null)
        {
            return "";
        }
        String t = type.toLowerCase(Locale.ROOT).trim();
        int paren = t.indexOf('(');
        if (paren > 0)
        {
            t = t.substring(0, paren).trim();
        }
        return t;
    }

    /**
     * Doris reports its V2 types (datev2/datetimev2/decimalv3) and TEXT as STRING through
     * information_schema. Compare types through this canonical form so a refresh does not report a
     * difference that does not exist.
     */
    public static String canonicalType(String type)
    {
        if (type == null)
        {
            return "";
        }
        String t = type.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        int paren = t.indexOf('(');
        String base = paren > 0 ? t.substring(0, paren).trim() : t;
        String suffix = paren > 0 ? t.substring(paren).replaceAll("\\s+", "") : "";
        base = switch (base)
        {
            case "datetimev2" -> "datetime";
            case "datev2" -> "date";
            case "decimalv3" -> "decimal";
            case "text", "tinytext", "mediumtext", "longtext" -> "string";
            case "serial" -> "int";
            case "bigserial" -> "bigint";
            default -> base;
        };
        if (List.of("int", "bigint", "smallint", "tinyint", "largeint", "boolean", "date",
            "float", "double", "json", "string").contains(base)
            || ("datetime".equals(base) && "(0)".equals(suffix)))
        {
            suffix = "";
        }
        return base + suffix;
    }

    private static List<String> resolveKeyColumns(Map<String, PlannedColumn> byName,
        DorisTableOptions options, String partitionColumn)
    {
        if (!options.keyColumns().isEmpty())
        {
            return options.keyColumns().stream()
                .map(name -> resolveName(byName, name, "键列"))
                .toList();
        }
        List<String> derived = byName.values().stream()
            .filter(PlannedColumn::sourceKey)
            .map(PlannedColumn::name)
            .toList();
        if (derived.isEmpty() && partitionColumn != null)
        {
            return List.of(partitionColumn);
        }
        return derived;
    }

    private static boolean hasSourceKey(Map<String, PlannedColumn> byName)
    {
        return byName.values().stream().anyMatch(PlannedColumn::sourceKey);
    }

    private static String resolveName(Map<String, PlannedColumn> byName, String name, String role)
    {
        if (name == null || name.isBlank())
        {
            throw new DorisDdlException(null, "Doris " + role + "不能为空");
        }
        PlannedColumn column = byName.get(name.trim().toLowerCase(Locale.ROOT));
        if (column == null)
        {
            throw new DorisDdlException(name.trim(),
                "Doris " + role + " " + name.trim() + " 不在字段映射中");
        }
        return column.name();
    }

    private static String quotedList(List<String> names)
    {
        return names.stream().map(DorisDdlUtil::quote).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String escapeLiteral(String value)
    {
        return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim().replace("'", "''");
    }

    private static String normalize(String sourceType)
    {
        if (sourceType == null)
        {
            return "";
        }
        return sourceType.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static Kind kindOf(String normalized)
    {
        if (normalized.isEmpty())
        {
            return Kind.TEXT;
        }
        String base = normalized;
        int paren = base.indexOf('(');
        if (paren > 0)
        {
            base = base.substring(0, paren).trim();
        }
        base = base.replace(" unsigned", "").replace(" zerofill", "").trim();
        switch (base)
        {
            case "char":
            case "nchar":
            case "character":
            case "varchar":
            case "varchar2":
            case "nvarchar":
            case "nvarchar2":
            case "character varying":
            case "char varying":
            case "bpchar":
            case "uuid":
            case "uniqueidentifier":
            case "enum":
            case "set":
                return Kind.STRING;
            case "text":
            case "tinytext":
            case "mediumtext":
            case "longtext":
            case "clob":
            case "nclob":
            case "ntext":
            case "longvarchar":
            case "long varchar":
            case "long":
            case "string":
                return Kind.TEXT;
            case "int":
            case "integer":
            case "int4":
            case "mediumint":
            case "serial":
            case "serial4":
            case "year":
                return Kind.INTEGER;
            case "bigint":
            case "int8":
            case "largeint":
            case "bigserial":
            case "serial8":
                return Kind.BIGINT;
            case "smallint":
            case "int2":
            case "smallserial":
            case "serial2":
                return Kind.SMALLINT;
            case "tinyint":
            case "bit":
                return Kind.TINYINT;
            case "bool":
            case "boolean":
                return Kind.BOOLEAN;
            case "decimal":
            case "numeric":
            case "number":
            case "dec":
            case "fixed":
            case "money":
            case "smallmoney":
            case "decimalv3":
                return Kind.DECIMAL;
            case "float":
            case "real":
            case "float4":
            case "binary_float":
                return Kind.FLOAT;
            case "double":
            case "double precision":
            case "float8":
            case "binary_double":
                return Kind.DOUBLE;
            case "date":
            case "datev2":
                return Kind.DATE;
            case "time":
            case "timetz":
            case "time without time zone":
            case "time with time zone":
                return Kind.TIME;
            case "datetime":
            case "smalldatetime":
            case "timestamp":
            case "timestamp without time zone":
            case "timestamp with time zone":
            case "timestamptz":
            case "datetimev2":
                return Kind.TIMESTAMP;
            case "binary":
            case "varbinary":
            case "blob":
            case "longblob":
            case "mediumblob":
            case "tinyblob":
            case "bytea":
            case "image":
            case "raw":
                return Kind.BINARY;
            case "json":
            case "jsonb":
                return Kind.JSON;
            default:
                return Kind.RAW;
        }
    }
}
