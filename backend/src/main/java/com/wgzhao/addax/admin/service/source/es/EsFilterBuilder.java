package com.wgzhao.addax.admin.service.source.es;

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
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * Elasticsearch filter syntax: a Query DSL object, not SQL and not the reader's own OGNL filter.
 *
 * <p>{@code script}, {@code script_fields} and {@code runtime_mappings} execute server-side code and
 * are rejected, matching the MongoDB adapter's position on {@code $where}. The incremental marker
 * {@code __max__field} becomes a {@code range} condition appended with {@code bool.filter}.</p>
 */
@Component
@RequiredArgsConstructor
public class EsFilterBuilder
{
    private static final List<String> FORBIDDEN_KEYS = List.of(
        "script", "script_fields", "runtime_mappings");
    private static final String MATCH_ALL = "{\"match_all\":{}}";

    private final ObjectMapper objectMapper;

    public String defaultFilter()
    {
        return MATCH_ALL;
    }

    public String normalize(SourceRef source, String rawFilter)
    {
        if (rawFilter == null || rawFilter.isBlank() || "1=1".equals(rawFilter.trim()))
        {
            return defaultFilter();
        }
        JsonNode node;
        try
        {
            node = objectMapper.readTree(rawFilter.trim());
        }
        catch (Exception e)
        {
            throw new ApiException(400, "Elasticsearch 过滤条件不是合法的 JSON: " + e.getMessage());
        }
        if (node == null || !node.isObject())
        {
            throw new ApiException(400, "Elasticsearch 过滤条件必须是 Query DSL JSON 对象");
        }
        // Accept a pasted search body as a convenience, but never silently drop sort/size fields that
        // the reader would need; only the unambiguous single-key {"query": ...} form is unwrapped.
        if (node.size() == 1 && node.has("query") && node.get("query").isObject())
        {
            node = node.get("query");
        }
        if (node.isEmpty())
        {
            return defaultFilter();
        }
        rejectUnsafe(node);
        return node.toString();
    }

    public String combine(String baseFilter, String extraFilter)
    {
        String base = normalize(null, baseFilter);
        String extra = normalize(null, extraFilter);
        if (isMatchAll(base))
        {
            return extra;
        }
        if (isMatchAll(extra) || base.equals(extra))
        {
            return base;
        }
        try
        {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode filter = root.putObject("bool").putArray("filter");
            filter.add(objectMapper.readTree(base));
            filter.add(objectMapper.readTree(extra));
            return root.toString();
        }
        catch (Exception e)
        {
            throw new ApiException(400, "Elasticsearch 过滤条件无法合并: " + e.getMessage());
        }
    }

    public String buildIncrementalPredicate(VwEtlTableWithSource table,
        List<ColumnMappingService.ResolvedColumnMapping> mappings, String sourceColumn, Object maxValue)
    {
        if (sourceColumn == null || sourceColumn.isBlank())
        {
            throw new ApiException(400, "Elasticsearch 增量字段不能为空");
        }
        ColumnMappingService.ResolvedColumnMapping mapping = mappings.stream()
            .filter(item -> sourceColumn.equals(item.sourceColumnName()))
            .findFirst()
            .orElseThrow(() -> new ApiException(400,
                "Elasticsearch 增量字段 " + sourceColumn + " 不在字段映射中，请先刷新字段"));
        if (!mapping.sourceActive() || mapping.sourceColumn() == null)
        {
            throw new ApiException(400, "Elasticsearch 增量字段 " + sourceColumn + " 已失效，请先修复映射");
        }
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode condition = root.putObject("range").putObject(sourceColumn);
        condition.set("gt", rangeValue(mapping.sourceColumn(), maxValue));
        return root.toString();
    }

    private JsonNode rangeValue(EtlColumn column, Object value)
    {
        if (value == null)
        {
            throw new ApiException(400, "Elasticsearch 增量字段 " + column.getColumnName() + " 的最大值为空");
        }
        String type = column.getSourceType() == null
            ? "string" : column.getSourceType().toLowerCase(Locale.ROOT);
        try
        {
            return switch (type)
            {
                case "int", "bigint" -> objectMapper.getNodeFactory()
                    .numberNode(new BigDecimal(value.toString()).toBigIntegerExact());
                case "double", "float", "decimal" -> objectMapper.getNodeFactory()
                    .numberNode(new BigDecimal(value.toString()));
                case "boolean" -> objectMapper.getNodeFactory()
                    .booleanNode(Boolean.parseBoolean(value.toString()));
                case "timestamp" -> objectMapper.getNodeFactory().textNode(isoValue(value));
                case "binary" -> throw new ApiException(400,
                    "Elasticsearch 增量字段 " + column.getColumnName() + " 的类型 binary 不支持比较");
                default -> objectMapper.getNodeFactory().textNode(value.toString());
            };
        }
        catch (NumberFormatException | ArithmeticException e)
        {
            throw new ApiException(400, "增量字段 " + column.getColumnName()
                + " 的目标最大值 [" + value + "] 无法转换为 " + type);
        }
    }

    private static String isoValue(Object value)
    {
        OffsetDateTime time;
        if (value instanceof OffsetDateTime offsetDateTime)
        {
            time = offsetDateTime;
        }
        else if (value instanceof Instant instant)
        {
            time = instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        else if (value instanceof Date date)
        {
            time = date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        else if (value instanceof LocalDateTime localDateTime)
        {
            time = localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        else if (value instanceof LocalDate localDate)
        {
            time = localDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        }
        else if (value instanceof Number number)
        {
            time = Instant.ofEpochMilli(number.longValue())
                .atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        else
        {
            time = parseText(value.toString().trim());
        }
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }

    private static OffsetDateTime parseText(String text)
    {
        for (String pattern : List.of("yyyyMMdd", "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss"))
        {
            try
            {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                if (pattern.length() == 8)
                {
                    return LocalDate.parse(text, formatter)
                        .atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
                }
                if (pattern.length() == 10)
                {
                    return LocalDate.parse(text, formatter)
                        .atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
                }
                return LocalDateTime.parse(text, formatter)
                    .atZone(ZoneId.systemDefault()).toOffsetDateTime();
            }
            catch (DateTimeParseException ignored)
            {
                // try the next shape
            }
        }
        try
        {
            return OffsetDateTime.parse(text);
        }
        catch (DateTimeParseException e)
        {
            throw new ApiException(400, "增量字段的日期值 [" + text + "] 无法解析");
        }
    }

    private boolean isMatchAll(String filter)
    {
        try
        {
            JsonNode node = objectMapper.readTree(filter);
            return node.isObject() && node.size() == 1 && node.has("match_all");
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static void rejectUnsafe(JsonNode node)
    {
        if (node.isObject())
        {
            var fields = node.fields();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (FORBIDDEN_KEYS.contains(entry.getKey()))
                {
                    throw new ApiException(400, "Elasticsearch 过滤条件不允许使用 " + entry.getKey());
                }
                rejectUnsafe(entry.getValue());
            }
        }
        else if (node.isArray())
        {
            node.forEach(EsFilterBuilder::rejectUnsafe);
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
