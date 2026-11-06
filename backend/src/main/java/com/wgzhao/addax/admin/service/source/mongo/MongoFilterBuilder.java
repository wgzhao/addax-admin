package com.wgzhao.addax.admin.service.source.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.source.SourceRef;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MongoDB filter syntax: a query document in extended JSON, not SQL.
 *
 * <p>{@code $where}, {@code $function} and {@code $accumulator} execute server-side JavaScript and
 * are rejected. The incremental marker {@code __max__field} becomes a {@code $gt} condition whose
 * BSON type follows the mapped source type.</p>
 */
@Component
@RequiredArgsConstructor
public class MongoFilterBuilder
{
    private static final List<String> FORBIDDEN_KEYS = List.of("$where", "$function", "$accumulator");

    private final ObjectMapper objectMapper;

    public String defaultFilter()
    {
        return "{}";
    }

    public String normalize(SourceRef source, String rawFilter)
    {
        if (rawFilter == null || rawFilter.isBlank() || "1=1".equals(rawFilter.trim()))
        {
            return defaultFilter();
        }
        String filter = rawFilter.trim();
        Document document;
        try
        {
            document = Document.parse(filter);
        }
        catch (RuntimeException e)
        {
            throw new ApiException(400, "MongoDB 过滤条件不是合法的 extended JSON: " + e.getMessage());
        }
        rejectUnsafe(document);
        return filter;
    }

    public String combine(String baseFilter, String extraFilter)
    {
        String base = normalize(null, baseFilter);
        String extra = normalize(null, extraFilter);
        if ("{}".equals(base))
        {
            return extra;
        }
        if ("{}".equals(extra) || base.equals(extra))
        {
            return base;
        }
        try
        {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode and = root.putArray("$and");
            and.add(objectMapper.readTree(base));
            and.add(objectMapper.readTree(extra));
            return root.toString();
        }
        catch (Exception e)
        {
            throw new ApiException(400, "MongoDB 过滤条件无法合并: " + e.getMessage());
        }
    }

    public String buildIncrementalPredicate(VwEtlTableWithSource table,
        List<ColumnMappingService.ResolvedColumnMapping> mappings, String sourceColumn, Object maxValue)
    {
        if (sourceColumn == null || sourceColumn.isBlank())
        {
            throw new ApiException(400, "MongoDB 增量字段不能为空");
        }
        ColumnMappingService.ResolvedColumnMapping mapping = mappings.stream()
            .filter(item -> sourceColumn.equals(item.sourceColumnName()))
            .findFirst()
            .orElseThrow(() -> new ApiException(400,
                "MongoDB 增量字段 " + sourceColumn + " 不在字段映射中，请先刷新字段"));
        if (!mapping.sourceActive() || mapping.sourceColumn() == null)
        {
            throw new ApiException(400, "MongoDB 增量字段 " + sourceColumn + " 已失效，请先修复映射");
        }
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode condition = root.putObject(sourceColumn);
        condition.set("$gt", bsonValue(mapping.sourceColumn(), maxValue));
        return root.toString();
    }

    /**
     * Extended JSON that preserves the BSON type of the target MAX. A plain JSON number would become
     * int32 or int64 depending on its size, and MongoDB's type bracketing makes a comparison of a
     * double/int32 field against another numeric BSON type match nothing.
     */
    private JsonNode bsonValue(EtlColumn column, Object value)
    {
        String type = column.getSourceType() == null
            ? "string" : column.getSourceType().toLowerCase(Locale.ROOT);
        try
        {
            return switch (type)
            {
                case "int" -> objectMapper.createObjectNode()
                    .put("$numberInt", new java.math.BigDecimal(value.toString()).toBigIntegerExact().toString());
                case "bigint" -> objectMapper.createObjectNode()
                    .put("$numberLong", new java.math.BigDecimal(value.toString()).toBigIntegerExact().toString());
                case "double", "float" -> objectMapper.createObjectNode()
                    .put("$numberDouble", new java.math.BigDecimal(value.toString()).toString());
                case "decimal" -> objectMapper.createObjectNode()
                    .put("$numberDecimal", new java.math.BigDecimal(value.toString()).toString());
                case "boolean" -> objectMapper.getNodeFactory().booleanNode(Boolean.parseBoolean(value.toString()));
                case "timestamp" -> objectMapper.createObjectNode()
                    .put("$date", timestampMillis(value));
                case "text", "binary" -> throw new ApiException(400,
                    "MongoDB 增量字段 " + column.getColumnName() + " 的类型 " + type + " 不支持比较");
                default -> objectMapper.getNodeFactory().textNode(value.toString());
            };
        }
        catch (NumberFormatException e)
        {
            // The target MAX is a string even though the source field is numeric. Treating it as a
            // string would compare different BSON types and silently match nothing, so fail loudly.
            throw new ApiException(400, "增量字段 " + column.getColumnName()
                + " 的目标最大值 [" + value + "] 无法转换为 " + type);
        }
    }

    /**
     * Extended JSON v2 wrapped form. The driver's parser accepts it and keeps the BSON type, which a
     * plain ISO string would not.
     */
    private long timestampMillis(Object value)
    {
        if (value instanceof Date date)
        {
            return date.getTime();
        }
        if (value instanceof Instant instant)
        {
            return instant.toEpochMilli();
        }
        if (value instanceof OffsetDateTime offsetDateTime)
        {
            return offsetDateTime.toInstant().toEpochMilli();
        }
        if (value instanceof LocalDateTime localDateTime)
        {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof LocalDate localDate)
        {
            return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        String text = value == null ? "" : value.toString().trim();
        for (String pattern : List.of("yyyyMMdd", "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss"))
        {
            try
            {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                if (pattern.equals("yyyyMMdd"))
                {
                    return LocalDate.parse(text, formatter)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
                if (pattern.equals("yyyy-MM-dd"))
                {
                    return LocalDate.parse(text, formatter)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                }
                return LocalDateTime.parse(text, formatter)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            catch (DateTimeParseException ignored)
            {
                // try the next shape
            }
        }
        try
        {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        }
        catch (DateTimeParseException e)
        {
            throw new ApiException(400, "增量字段的日期值 [" + text + "] 无法解析");
        }
    }

    private static void rejectUnsafe(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                String key = String.valueOf(entry.getKey());
                if (FORBIDDEN_KEYS.contains(key))
                {
                    throw new ApiException(400, "MongoDB 过滤条件不允许使用 " + key);
                }
                rejectUnsafe(entry.getValue());
            }
        }
        else if (value instanceof List<?> list)
        {
            list.forEach(MongoFilterBuilder::rejectUnsafe);
        }
    }

    public boolean isIncrementalFilter(String rawFilter)
    {
        return rawFilter != null && rawFilter.startsWith(Constants.SPECIAL_FILTER_PLACEHOLDER);
    }

    public String incrementalColumn(String rawFilter)
    {
        return rawFilter.substring(Constants.SPECIAL_FILTER_PLACEHOLDER.length()).trim();
    }
}
