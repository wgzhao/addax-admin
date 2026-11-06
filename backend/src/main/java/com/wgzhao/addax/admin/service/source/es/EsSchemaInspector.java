package com.wgzhao.addax.admin.service.source.es;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.dto.SourceFieldMeta;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.service.source.SchemaMode;
import com.wgzhao.addax.admin.service.source.SourceAccessException;
import com.wgzhao.addax.admin.service.source.SourceField;
import com.wgzhao.addax.admin.service.source.SourceRef;
import com.wgzhao.addax.admin.service.source.SourceSchema;
import com.wgzhao.addax.admin.service.source.SourceTableHints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Discovers index aliases and fields from the Elasticsearch mapping, then samples documents to
 * correct mappings that do not describe what Addax will actually read.
 *
 * <p>The Addax reader reads {@code _source} values by flat top-level key, and converts objects and
 * arrays to JSON text. A field therefore has to be judged by the runtime shape, not only by the
 * mapping: a {@code long} field carrying strings, or a scalar field carrying arrays, changes the
 * Addax column type and must not be mapped as if the mapping were the truth.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EsSchemaInspector
{
    private static final int MAX_STRING_LENGTH = 16383;
    private static final int MIN_STRING_LENGTH = 255;
    private static final int MAX_SAMPLE_SIZE = 10_000;
    /**
     * Metadata fields are not part of {@code _source}; a user field such as {@code _class} is a
     * normal document field and must stay collectable.
     */
    private static final Set<String> RESERVED_FIELDS = Set.of(
        "_id", "_index", "_type", "_score", "_source", "_routing", "_version",
        "_seq_no", "_primary_term", "_field_names", "_ignored", "_meta");

    private final EsClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    @Value("${addax.source.es.sampleSize:1000}")
    private int sampleSize;

    @Value("${addax.source.es.maxFields:500}")
    private int maxFields;

    public SourceSchema inspect(SourceRef source, String namespace, String index, SchemaMode mode)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        try (EsClient client = clientFactory.create(source, config))
        {
            MappingInfo mapping = readMapping(client, index);
            if (mapping.allSourceDisabled)
            {
                throw new SourceAccessException("索引 " + index
                    + " 关闭了 _source，elasticsearchreader 无法读取文档", false);
            }
            List<Map<String, Object>> samples = sample(client, index);
            return infer(mapping, samples);
        }
        catch (SourceAccessException e)
        {
            throw e;
        }
        catch (EsClient.EsHttpException e)
        {
            if (e.status() == 404)
            {
                throw new SourceAccessException("索引或别名不存在: " + index, false, e);
            }
            throw new SourceAccessException("读取 Elasticsearch 元数据失败: " + e.getMessage(),
                e.status() >= 500, e);
        }
        catch (RuntimeException e)
        {
            throw new SourceAccessException("读取 Elasticsearch 元数据失败: " + e.getMessage(), true, e);
        }
    }

    public SourceTableHints tableHints(SourceRef source, String namespace, String index)
    {
        // Elasticsearch has no primary key and no NOT NULL contract: _id is not part of _source and
        // therefore cannot be collected by the reader.
        return SourceTableHints.EMPTY;
    }

    public List<TableMetaDto> listTables(SourceRef source, String namespace)
    {
        EsConnectConfig config = EsConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        try (EsClient client = clientFactory.create(source, config))
        {
            Map<String, Long> indices = listIndices(client);
            Map<String, Long> aliases = listAliases(client, indices);
            List<TableMetaDto> result = new ArrayList<>(indices.size() + aliases.size());
            indices.forEach((name, count) -> result.add(new TableMetaDto(name, "", count)));
            aliases.forEach((name, count) -> result.add(new TableMetaDto(name, "Elasticsearch alias", count)));
            result.sort(Comparator.comparing(TableMetaDto::name));
            return result;
        }
        catch (SourceAccessException e)
        {
            throw e;
        }
        catch (EsClient.EsHttpException e)
        {
            if (e.status() == 401 || e.status() == 403)
            {
                throw new SourceAccessException("Elasticsearch 认证失败: " + e.getMessage(), false, e);
            }
            throw new SourceAccessException("读取 Elasticsearch 索引列表失败: " + e.getMessage(), true, e);
        }
        catch (RuntimeException e)
        {
            throw new SourceAccessException("读取 Elasticsearch 索引列表失败: " + e.getMessage(), true, e);
        }
    }

    private Map<String, Long> listIndices(EsClient client)
    {
        Map<String, Long> counts = new LinkedHashMap<>();
        try
        {
            JsonNode rows = client.get("/_cat/indices?format=json&h=index,docs.count,store.size,status");
            for (JsonNode row : rows)
            {
                addIndex(counts, row.path("index").asText(""), row.path("docs.count"));
            }
            return counts;
        }
        catch (EsClient.EsHttpException e)
        {
            // _cat requires monitor privileges. Mapping keys are enough to offer the index as a table;
            // the row count is optional metadata and must not block the whole flow.
            log.debug("_cat/indices failed ({}), falling back to _mapping", e.getMessage());
        }
        JsonNode mappings = client.get("/_mapping");
        mappings.fieldNames().forEachRemaining(name -> addIndex(counts, name, null));
        return counts;
    }

    private static void addIndex(Map<String, Long> counts, String name, JsonNode docsCount)
    {
        if (name == null || name.isBlank() || name.startsWith("."))
        {
            return;
        }
        counts.put(name, parseCount(docsCount));
    }

    private static Long parseCount(JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return null;
        }
        String text = node.asText("");
        if (text.isBlank() || "null".equalsIgnoreCase(text))
        {
            return null;
        }
        try
        {
            return Long.parseLong(text);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private Map<String, Long> listAliases(EsClient client, Map<String, Long> indices)
    {
        Map<String, Long> aliases = new LinkedHashMap<>();
        JsonNode root;
        try
        {
            root = client.get("/_alias");
        }
        catch (EsClient.EsHttpException e)
        {
            log.debug("_alias failed ({}), aliases are omitted", e.getMessage());
            return aliases;
        }
        root.fields().forEachRemaining(indexEntry -> {
            String index = indexEntry.getKey();
            if (index.startsWith("."))
            {
                return;
            }
            Long count = indices.get(index);
            indexEntry.getValue().path("aliases").fieldNames().forEachRemaining(alias -> {
                Long current = aliases.get(alias);
                if (current == null && count == null)
                {
                    aliases.put(alias, null);
                }
                else if (count != null)
                {
                    aliases.put(alias, (current == null ? 0 : current) + count);
                }
            });
        });
        return aliases;
    }

    private List<Map<String, Object>> sample(EsClient client, String index)
    {
        int limit = Math.max(1, Math.min(sampleSize, MAX_SAMPLE_SIZE));
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", limit);
        body.put("track_total_hits", false);
        body.putObject("query").putObject("match_all");
        body.putArray("sort").add("_doc");
        JsonNode response = client.post("/" + EsClient.encodePath(index) + "/_search", body);
        JsonNode hits = response.path("hits").path("hits");
        if (!hits.isArray() || hits.isEmpty())
        {
            return List.of();
        }
        List<Map<String, Object>> samples = new ArrayList<>(hits.size());
        int withoutSource = 0;
        for (JsonNode hit : hits)
        {
            JsonNode source = hit.get("_source");
            if (source == null || source.isNull())
            {
                withoutSource++;
                continue;
            }
            Map<String, Object> document = objectMapper.convertValue(source,
                new TypeReference<Map<String, Object>>() {});
            if (document != null)
            {
                samples.add(document);
            }
        }
        if (withoutSource == hits.size())
        {
            throw new SourceAccessException("索引 " + index
                + " 未返回 _source，elasticsearchreader 无法读取文档（请检查 _source 设置或查询）", false);
        }
        return samples;
    }

    private MappingInfo readMapping(EsClient client, String index)
    {
        JsonNode response = client.get("/" + EsClient.encodePath(index) + "/_mapping");
        MappingInfo info = new MappingInfo();
        boolean anyIndex = false;
        int disabled = 0;
        var indexFields = response.fields();
        while (indexFields.hasNext())
        {
            var indexEntry = indexFields.next();
            anyIndex = true;
            JsonNode mappings = indexEntry.getValue().path("mappings");
            JsonNode source = mappings.path("_source");
            if (source.isObject() && !source.path("enabled").asBoolean(true))
            {
                disabled++;
            }
            JsonNode properties = mappings.path("properties");
            if (properties.isObject())
            {
                properties.fields().forEachRemaining(entry ->
                    mergeProperty(info.properties, entry.getKey(), entry.getValue(), info));
            }
            JsonNode runtime = mappings.path("runtime");
            if (runtime.isObject())
            {
                runtime.fieldNames().forEachRemaining(name -> info.runtimeFields.add(name));
            }
        }
        if (!anyIndex)
        {
            throw new SourceAccessException("索引或别名不存在: " + index, false);
        }
        info.allSourceDisabled = disabled > 0 && disabled == response.size();
        info.someSourceDisabled = disabled > 0 && !info.allSourceDisabled;
        resolveAliases(info);
        return info;
    }

    private void mergeProperty(Map<String, PropertyInfo> properties, String name, JsonNode node,
        MappingInfo info)
    {
        PropertyInfo property = new PropertyInfo();
        property.type = node.path("type").asText("");
        property.objectLike = node.has("properties")
            || "object".equals(property.type) || "nested".equals(property.type)
            || "flattened".equals(property.type);
        if (property.type.isBlank())
        {
            property.type = property.objectLike ? "object" : "unknown";
        }
        if ("alias".equals(property.type))
        {
            property.aliasPath = node.path("path").asText("");
        }
        PropertyInfo existing = properties.get(name);
        if (existing == null)
        {
            properties.put(name, property);
            return;
        }
        if (!existing.type.equals(property.type) || existing.objectLike != property.objectLike)
        {
            existing.conflicting = true;
            existing.objectLike = existing.objectLike || property.objectLike;
        }
    }

    private void resolveAliases(MappingInfo info)
    {
        for (Map.Entry<String, PropertyInfo> entry : info.properties.entrySet())
        {
            PropertyInfo property = entry.getValue();
            if (!"alias".equals(property.type))
            {
                continue;
            }
            PropertyInfo target = info.properties.get(property.aliasPath);
            if (target == null || target.conflicting)
            {
                // An alias to a nested sub-field cannot be read from _source by its top-level name.
                property.unresolved = true;
                continue;
            }
            property.type = target.type;
            property.objectLike = target.objectLike;
            property.conflicting = target.conflicting;
        }
    }

    private SourceSchema infer(MappingInfo mapping, List<Map<String, Object>> samples)
    {
        List<String> warnings = new ArrayList<>();
        Map<String, SampleStat> stats = collectStats(samples);
        List<SourceField> fields = new ArrayList<>();
        Set<String> usedTargetNames = new LinkedHashSet<>();
        boolean truncated = false;
        int objectFields = 0;
        int mixedFields = 0;

        if (!mapping.runtimeFields.isEmpty())
        {
            warnings.add("运行时字段（runtime）不在 _source 中，elasticsearchreader 无法采集，已跳过: "
                + String.join(", ", mapping.runtimeFields));
        }
        if (mapping.someSourceDisabled)
        {
            warnings.add("别名覆盖的部分索引关闭了 _source，这些索引的文档会被跳过");
        }
        if (mapping.properties.isEmpty() && samples.isEmpty())
        {
            warnings.add("索引为空且没有 mapping 字段；请先写入数据或手工添加源字段");
        }

        for (Map.Entry<String, PropertyInfo> entry : mapping.properties.entrySet())
        {
            String name = entry.getKey();
            PropertyInfo property = entry.getValue();
            if (!isAddressable(name))
            {
                warnings.add("字段 " + name + " 无法被 elasticsearchreader 按顶层字段寻址，已跳过");
                continue;
            }
            if (property.unresolved)
            {
                warnings.add("字段 " + name + " 是别名，但指向的目标字段无法从 _source 顶层读取，已跳过");
                continue;
            }
            if (property.conflicting)
            {
                warnings.add("字段 " + name + " 在多个后备索引中的 mapping 不一致，按采样和首个 mapping 类型保守处理");
            }
            if (fields.size() >= maxFields)
            {
                truncated = true;
                break;
            }
            SampleStat stat = stats.remove(name);
            if (stat == null)
            {
                stat = new SampleStat();
            }
            if (property.objectLike)
            {
                objectFields++;
            }
            String portable = portableType(name, property, stat, warnings);
            if (!"text".equals(portable) && hasCompositeRuntime(stat))
            {
                portable = "text";
            }
            if (isMixed(stat))
            {
                mixedFields++;
            }
            String nativeType = property.type;
            fields.add(new SourceField(name, portable, nativeType, lengthFor(portable, stat),
                "decimal".equals(portable) ? 38 : lengthFor(portable, stat),
                "decimal".equals(portable) ? 10 : 0,
                commentFor(nativeType), true, false,
                uniqueTargetName(sanitize(name), usedTargetNames),
                meta(stat, samples.size())));
        }

        int sampledOnly = 0;
        for (Map.Entry<String, SampleStat> entry : stats.entrySet())
        {
            String name = entry.getKey();
            if (!isAddressable(name))
            {
                warnings.add("字段 " + name + " 无法被 elasticsearchreader 按顶层字段寻址，已跳过");
                continue;
            }
            if (fields.size() >= maxFields)
            {
                truncated = true;
                break;
            }
            SampleStat stat = entry.getValue();
            String portable = inferFromSample(stat);
            sampledOnly++;
            fields.add(new SourceField(name, portable, stat.dominantLabel(), lengthFor(portable, stat),
                "decimal".equals(portable) ? 38 : lengthFor(portable, stat),
                "decimal".equals(portable) ? 10 : 0,
                commentFor(stat.dominantLabel()), true, false,
                uniqueTargetName(sanitize(name), usedTargetNames),
                meta(stat, samples.size())));
        }
        if (sampledOnly > 0)
        {
            warnings.add(sampledOnly + " 个字段只存在于文档中而 mapping 未声明（可能是 dynamic=false），已按采样推断类型");
        }
        if (objectFields > 0)
        {
            warnings.add(objectFields + " 个嵌套/对象字段按 JSON 文本读取，子字段不能单独作为采集列");
        }
        if (mixedFields > 0)
        {
            warnings.add(mixedFields + " 个字段的文档实际类型与 mapping 不一致，已按采样类型保守映射");
        }
        if (truncated)
        {
            warnings.add("字段数超过上限 " + maxFields + "，已截断；可提高 addax.source.es.maxFields");
        }
        return new SourceSchema(fields, warnings, samples.size(), truncated);
    }

    private Map<String, SampleStat> collectStats(List<Map<String, Object>> samples)
    {
        Map<String, SampleStat> stats = new LinkedHashMap<>();
        for (Map<String, Object> document : samples)
        {
            for (Map.Entry<String, Object> entry : document.entrySet())
            {
                String name = entry.getKey();
                if (!isAddressable(name))
                {
                    continue;
                }
                SampleStat stat = stats.computeIfAbsent(name, key -> new SampleStat());
                stat.total++;
                Object value = entry.getValue();
                if (value == null)
                {
                    stat.nullCount++;
                    stat.types.merge("null", 1, Integer::sum);
                    continue;
                }
                stat.present++;
                String label = label(value);
                stat.types.merge(label, 1, Integer::sum);
                if (value instanceof String text)
                {
                    stat.maxStringLength = Math.max(stat.maxStringLength, text.length());
                }
            }
        }
        return stats;
    }

    private String portableType(String name, PropertyInfo property, SampleStat stat, List<String> warnings)
    {
        String type = property.type.toLowerCase(Locale.ROOT);
        if (property.objectLike)
        {
            return "text";
        }
        switch (type)
        {
            case "date", "date_nanos":
                // _source keeps the original date text, so the reader emits a StringColumn.
                return "string";
            case "byte", "short", "integer", "token_count":
                return numericOrRuntime(stat, "int", "bigint", "double", name, warnings);
            case "long":
                return numericOrRuntime(stat, "bigint", "bigint", "double", name, warnings);
            case "unsigned_long":
                return numericOrRuntime(stat, "decimal", "decimal", "double", name, warnings);
            case "half_float", "float", "double", "scaled_float", "rank_feature":
                return numericOrRuntime(stat, "double", "double", "double", name, warnings);
            case "boolean":
                if (hasRuntime(stat, "string") || hasRuntime(stat, "object") || hasRuntime(stat, "array"))
                {
                    warnings.add("字段 " + name + " 的 mapping 是 boolean，但文档实际类型不是 boolean，已按采样映射");
                    return inferFromSample(stat);
                }
                return "boolean";
            case "keyword", "constant_keyword", "wildcard", "ip", "binary",
                 "completion", "search_as_you_type":
                if (stat.present > 0 && !hasRuntime(stat, "string"))
                {
                    warnings.add("字段 " + name + " 的 mapping 是 " + property.type
                        + "，但文档实际类型不是字符串，已按采样映射");
                    return inferFromSample(stat);
                }
                return "string";
            case "text", "match_only_text":
                return "text";
            case "object", "nested", "flattened", "geo_point", "geo_shape", "shape",
                 "dense_vector", "sparse_vector", "histogram", "aggregate_metric_double":
                return "text";
            case "rank_features":
                return "text";
            default:
                warnings.add("字段 " + name + " 的 mapping 类型 " + property.type + " 未识别，按 JSON 文本读取");
                return "text";
        }
    }

    /**
     * A numeric mapping still has to follow the runtime shape: when the documents carry strings the
     * reader produces StringColumns, and declaring a numeric target column would fail at write time.
     */
    private String numericOrRuntime(SampleStat stat, String numeric, String integer, String floating,
        String name, List<String> warnings)
    {
        if (stat.present == 0)
        {
            return numeric;
        }
        if (hasRuntime(stat, "string") || hasRuntime(stat, "object") || hasRuntime(stat, "array")
            || hasRuntime(stat, "boolean"))
        {
            warnings.add("字段 " + name + " 的 mapping 是数值类型，但文档实际类型不是数值，已按采样映射");
            return inferFromSample(stat);
        }
        if (hasRuntime(stat, "double"))
        {
            return floating;
        }
        return hasRuntime(stat, "long") ? integer : numeric;
    }

    private static boolean hasRuntime(SampleStat stat, String label)
    {
        return stat.types.getOrDefault(label, 0) > 0;
    }

    private static boolean hasCompositeRuntime(SampleStat stat)
    {
        return hasRuntime(stat, "object") || hasRuntime(stat, "array");
    }

    private static boolean isMixed(SampleStat stat)
    {
        Set<String> labels = new LinkedHashSet<>(stat.types.keySet());
        labels.remove("null");
        return labels.size() > 1;
    }

    private static String inferFromSample(SampleStat stat)
    {
        Set<String> labels = new LinkedHashSet<>(stat.types.keySet());
        labels.remove("null");
        if (labels.isEmpty())
        {
            return "string";
        }
        if (labels.stream().allMatch(label -> "long".equals(label)))
        {
            return "bigint";
        }
        if (labels.stream().allMatch(label -> "double".equals(label)))
        {
            return "double";
        }
        if (labels.stream().allMatch(label -> "boolean".equals(label)))
        {
            return "boolean";
        }
        if (labels.stream().allMatch(label -> "string".equals(label)))
        {
            return "string";
        }
        if (labels.stream().allMatch(label -> "object".equals(label) || "array".equals(label)))
        {
            return "text";
        }
        return "text";
    }

    private static int lengthFor(String portable, SampleStat stat)
    {
        if (!"string".equals(portable) && !"text".equals(portable))
        {
            return 0;
        }
        int length = Math.max(MIN_STRING_LENGTH, stat.maxStringLength);
        return Math.min(MAX_STRING_LENGTH, length);
    }

    private static SourceFieldMeta meta(SampleStat stat, int sampleCount)
    {
        int missing = Math.max(0, sampleCount - stat.total);
        Map<String, Integer> types = new LinkedHashMap<>(stat.types);
        types.remove("null");
        return new SourceFieldMeta(sampleCount, stat.present, missing, stat.nullCount, types);
    }

    private static String label(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof String)
        {
            return "string";
        }
        if (value instanceof Boolean)
        {
            return "boolean";
        }
        if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal)
        {
            return "double";
        }
        if (value instanceof Number)
        {
            return "long";
        }
        if (value instanceof Map)
        {
            return "object";
        }
        if (value instanceof List)
        {
            return "array";
        }
        return "unknown";
    }

    private static String commentFor(String nativeType)
    {
        return "Elasticsearch " + nativeType;
    }

    private static boolean isAddressable(String name)
    {
        if (name == null || name.isBlank() || name.length() > 255)
        {
            return false;
        }
        if (RESERVED_FIELDS.contains(name) || name.contains(".") || name.contains("*"))
        {
            // The reader filters _source with dotted include patterns; a literal dot is ambiguous.
            return false;
        }
        for (int i = 0; i < name.length(); i++)
        {
            if (name.charAt(i) < 0x20)
            {
                return false;
            }
        }
        return true;
    }

    private static String sanitize(String name)
    {
        String value = name.replace(".", "__").replaceAll("[^A-Za-z0-9_$]", "_");
        if (value.isEmpty() || (!Character.isLetter(value.charAt(0)) && value.charAt(0) != '_'))
        {
            value = "_" + value;
        }
        return value.length() > 120 ? value.substring(0, 120) : value;
    }

    private static String uniqueTargetName(String base, Set<String> used)
    {
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT)))
        {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private static final class MappingInfo
    {
        private final Map<String, PropertyInfo> properties = new LinkedHashMap<>();
        private final Set<String> runtimeFields = new LinkedHashSet<>();
        private boolean allSourceDisabled;
        private boolean someSourceDisabled;
    }

    private static final class PropertyInfo
    {
        private String type;
        private String aliasPath;
        private boolean objectLike;
        private boolean conflicting;
        private boolean unresolved;
    }

    private static final class SampleStat
    {
        private final Map<String, Integer> types = new LinkedHashMap<>();
        private int total;
        private int present;
        private int nullCount;
        private int maxStringLength;

        private String dominantLabel()
        {
            String best = "string";
            int count = -1;
            for (Map.Entry<String, Integer> entry : types.entrySet())
            {
                if ("null".equals(entry.getKey()))
                {
                    continue;
                }
                if (entry.getValue() > count)
                {
                    count = entry.getValue();
                    best = entry.getKey();
                }
            }
            return best;
        }
    }
}
