package com.wgzhao.addax.admin.service.source.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import com.wgzhao.addax.admin.service.source.SchemaMode;
import com.wgzhao.addax.admin.service.source.SourceAccessException;
import com.wgzhao.addax.admin.service.source.SourceField;
import com.wgzhao.addax.admin.service.source.SourceRef;
import com.wgzhao.addax.admin.service.source.SourceSchema;
import com.wgzhao.addax.admin.service.source.SourceTableHints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Infers a MongoDB collection's shape by reading a bounded, deterministic sample.
 *
 * <p>{@code $sample} is deliberately not used: it returns a different set of documents on every
 * refresh, which makes the target schema churn. Reading the first and last N/2 documents by
 * {@code _id} is stable and still covers both old and new documents.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoSchemaInspector
{
    private static final int MAX_STRING_LENGTH = 16383;

    private final MongoClientFactory clientFactory;

    @Value("${addax.source.mongo.sampleSize:1000}")
    private int sampleSize;

    @Value("${addax.source.mongo.maxDepth:5}")
    private int maxDepth;

    @Value("${addax.source.mongo.maxFields:500}")
    private int maxFields;

    public SourceSchema inspect(SourceRef source, String database, String collection, SchemaMode mode)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        try (MongoClient client = clientFactory.create(source, config))
        {
            MongoDatabase db = client.getDatabase(database);
            if (!collectionExists(db, collection))
            {
                throw new SourceAccessException("源集合不存在: " + database + "." + collection, false);
            }
            MongoCollection<Document> coll = db.getCollection(collection);
            long count = estimatedCount(coll);
            List<Document> samples = sample(coll, count);
            return infer(samples, count);
        }
        catch (SourceAccessException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw new SourceAccessException("读取 MongoDB 元数据失败: " + e.getMessage(), true, e);
        }
    }

    public SourceTableHints tableHints(SourceRef source, String database, String collection)
    {
        MongoConnectConfig config = MongoConnectConfig.fromSource(source);
        config.validate(source.username(), source.password());
        try (MongoClient client = clientFactory.create(source, config))
        {
            Document first = client.getDatabase(database).getCollection(collection).find().first();
            if (first == null)
            {
                return SourceTableHints.EMPTY;
            }
            Object id = first.get("_id");
            if (id instanceof String || id instanceof Integer || id instanceof Long || id instanceof ObjectId)
            {
                return new SourceTableHints(List.of("_id"), Set.of(), Map.of());
            }
            return SourceTableHints.EMPTY;
        }
        catch (RuntimeException e)
        {
            log.warn("Failed to read MongoDB table hints for {}.{}: {}", database, collection, e.getMessage());
            return SourceTableHints.EMPTY;
        }
    }

    private boolean collectionExists(MongoDatabase db, String collection)
    {
        for (String name : db.listCollectionNames())
        {
            if (name.equals(collection))
            {
                return true;
            }
        }
        return false;
    }

    private long estimatedCount(MongoCollection<Document> coll)
    {
        try
        {
            return coll.estimatedDocumentCount();
        }
        catch (RuntimeException e)
        {
            log.debug("estimatedDocumentCount failed: {}", e.getMessage());
            return 0;
        }
    }

    private List<Document> sample(MongoCollection<Document> coll, long count)
    {
        int limit = Math.max(1, sampleSize);
        List<Document> samples = new ArrayList<>();
        if (count > 0 && count <= limit)
        {
            coll.find().into(samples);
            return samples;
        }
        int half = Math.max(1, limit / 2);
        coll.find().sort(Sorts.ascending("_id")).limit(half).into(samples);
        Set<Object> seen = new LinkedHashSet<>();
        for (Document doc : samples)
        {
            seen.add(doc.get("_id"));
        }
        List<Document> tail = new ArrayList<>();
        coll.find().sort(Sorts.descending("_id")).limit(half).into(tail);
        for (Document doc : tail)
        {
            if (seen.add(doc.get("_id")))
            {
                samples.add(doc);
            }
        }
        if (samples.isEmpty() && count == 0)
        {
            // The estimate can be zero on a collection the user just filled; a bounded scan still
            // gives the UI something to work with.
            coll.find().limit(limit).into(samples);
        }
        return samples;
    }

    private SourceSchema infer(List<Document> samples, long count)
    {
        List<String> warnings = new ArrayList<>();
        if (samples.isEmpty())
        {
            warnings.add("集合为空，仅推断出 _id；请手工添加字段或等待数据写入后重新采样");
            SourceField id = new SourceField("_id", "string", "objectId", 24, 24, 0,
                "MongoDB 集合为空，未采样到字段", false, true, "_id");
            return new SourceSchema(List.of(id), warnings, 0, false);
        }

        Map<String, PathInfo> paths = new LinkedHashMap<>();
        for (Document doc : samples)
        {
            walk(doc, "", 0, paths);
        }

        // A path that is a document in some documents and a scalar in others collapses into one text
        // leaf; its children are not addressable consistently, so suppress them.
        Set<String> suppressed = new LinkedHashSet<>();
        for (Map.Entry<String, PathInfo> entry : paths.entrySet())
        {
            PathInfo info = entry.getValue();
            if (info.documentSeen && info.nonDocumentSeen)
            {
                // The mixed path itself is emitted as one text leaf; only its children are suppressed.
                for (String other : paths.keySet())
                {
                    if (other.startsWith(entry.getKey() + "."))
                    {
                        suppressed.add(other);
                    }
                }
            }
        }

        List<SourceField> fields = new ArrayList<>();
        Set<String> usedTargetNames = new LinkedHashSet<>();
        boolean truncated = false;
        boolean depthTruncated = false;
        int mixedTypeFields = 0;

        // _id is always first; Mongo creates it even when the inserted document did not carry one.
        PathInfo idInfo = paths.get("_id");
        if (idInfo != null)
        {
            Kind idKind = resolveKind(idInfo);
            int idLength = lengthFor(idKind, idInfo);
            fields.add(new SourceField("_id", sourceType(idKind), nativeType(idInfo, samples.size()),
                idLength, idKind == Kind.DECIMAL ? 38 : idLength, idKind == Kind.DECIMAL ? 10 : 0,
                nativeType(idInfo, samples.size()), false, true, "_id"));
        }
        else
        {
            fields.add(new SourceField("_id", "string", "objectId", 24, 24, 0,
                "MongoDB 自动生成的主键", false, true, "_id"));
        }
        usedTargetNames.add("_id");

        for (Map.Entry<String, PathInfo> entry : paths.entrySet())
        {
            String path = entry.getKey();
            PathInfo info = entry.getValue();
            if ("_id".equals(path) || suppressed.contains(path))
            {
                continue;
            }
            boolean pureDocument = info.documentSeen && !info.nonDocumentSeen;
            boolean hasChildren = info.hasChildren;
            if (pureDocument && hasChildren && info.depth < maxDepth)
            {
                // Children are emitted as their own fields; the parent is only a namespace.
                continue;
            }
            if (fields.size() >= maxFields)
            {
                truncated = true;
                break;
            }
            if (!isAddressable(path))
            {
                warnings.add("字段 " + path + " 的命名无法被 mongodbreader 寻址，已跳过");
                continue;
            }
            Kind kind = resolveKind(info);
            if (kind == Kind.MIXED)
            {
                mixedTypeFields++;
            }
            if (info.depth >= maxDepth && info.documentSeen)
            {
                depthTruncated = true;
            }
            String nativeType = nativeType(info, samples.size());
            int length = lengthFor(kind, info);
            String sourceType = sourceType(kind);
            String targetName = uniqueTargetName(sanitize(path), usedTargetNames);
            fields.add(new SourceField(path, sourceType, nativeType, length,
                kind == Kind.DECIMAL ? 38 : length,
                kind == Kind.DECIMAL ? 10 : 0,
                nativeType,
                !"_id".equals(path),
                "_id".equals(path),
                targetName));
        }

        if (truncated)
        {
            warnings.add("字段数超过上限 " + maxFields + "，已截断；可提高 addax.source.mongo.maxFields");
        }
        if (depthTruncated)
        {
            warnings.add("嵌套深度超过 " + maxDepth + "，深层文档按 JSON 文本读取");
        }
        if (mixedTypeFields > 0)
        {
            warnings.add(mixedTypeFields + " 个字段存在混合类型，已保守映射为 text");
        }
        return new SourceSchema(fields, warnings, samples.size(), truncated || depthTruncated);
    }

    private void walk(Document doc, String prefix, int depth, Map<String, PathInfo> paths)
    {
        for (Map.Entry<String, Object> entry : doc.entrySet())
        {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            PathInfo info = paths.computeIfAbsent(path, key -> new PathInfo());
            info.depth = Math.min(info.depth, depth);
            if (value instanceof Document child)
            {
                info.documentSeen = true;
                info.counts.merge(Kind.DOCUMENT, 1, Integer::sum);
                if (depth + 1 >= maxDepth)
                {
                    info.depthTruncated = true;
                    continue;
                }
                if (!child.isEmpty())
                {
                    info.hasChildren = true;
                    walk(child, path, depth + 1, paths);
                }
            }
            else if (value == null)
            {
                info.counts.merge(Kind.NULL, 1, Integer::sum);
            }
            else
            {
                info.nonDocumentSeen = true;
                Kind kind = kindOf(value);
                info.counts.merge(kind, 1, Integer::sum);
                if (kind == Kind.STRING)
                {
                    info.maxStringLength = Math.max(info.maxStringLength, value.toString().length());
                }
            }
        }
    }

    private static Kind kindOf(Object value)
    {
        if (value instanceof String)
        {
            return Kind.STRING;
        }
        if (value instanceof Integer)
        {
            return Kind.INT32;
        }
        if (value instanceof Long)
        {
            return Kind.INT64;
        }
        if (value instanceof Double || value instanceof Float)
        {
            return Kind.DOUBLE;
        }
        if (value instanceof Decimal128 || value instanceof BigDecimal)
        {
            return Kind.DECIMAL;
        }
        if (value instanceof Boolean)
        {
            return Kind.BOOLEAN;
        }
        if (value instanceof Date || value instanceof Instant)
        {
            return Kind.DATE;
        }
        if (value instanceof ObjectId)
        {
            return Kind.OBJECT_ID;
        }
        if (value instanceof List)
        {
            return Kind.ARRAY;
        }
        if (value instanceof byte[])
        {
            return Kind.BINARY;
        }
        return Kind.OTHER;
    }

    private static Kind resolveKind(PathInfo info)
    {
        Set<Kind> kinds = new LinkedHashSet<>(info.counts.keySet());
        kinds.remove(Kind.NULL);
        if (kinds.isEmpty())
        {
            return Kind.STRING;
        }
        boolean numeric = kinds.stream().allMatch(k ->
            k == Kind.INT32 || k == Kind.INT64 || k == Kind.DOUBLE || k == Kind.DECIMAL);
        if (numeric)
        {
            if (kinds.contains(Kind.DECIMAL))
            {
                return Kind.DECIMAL;
            }
            if (kinds.contains(Kind.DOUBLE))
            {
                return Kind.DOUBLE;
            }
            return kinds.contains(Kind.INT64) ? Kind.BIGINT : Kind.INT32;
        }
        if (kinds.size() == 1)
        {
            Kind only = kinds.iterator().next();
            return switch (only)
            {
                case DOCUMENT, ARRAY, OTHER -> Kind.TEXT;
                case OBJECT_ID -> Kind.STRING;
                default -> only;
            };
        }
        if (kinds.contains(Kind.STRING))
        {
            return Kind.TEXT;
        }
        if (kinds.stream().allMatch(k -> k == Kind.DOCUMENT || k == Kind.ARRAY))
        {
            return Kind.TEXT;
        }
        return Kind.MIXED;
    }

    private static String sourceType(Kind kind)
    {
        return switch (kind)
        {
            case STRING -> "string";
            case INT32 -> "int";
            case INT64, BIGINT -> "bigint";
            case DOUBLE -> "double";
            case DECIMAL -> "decimal";
            case BOOLEAN -> "boolean";
            case DATE -> "timestamp";
            case BINARY -> "binary";
            default -> "text";
        };
    }

    private static int lengthFor(Kind kind, PathInfo info)
    {
        if (kind == Kind.STRING)
        {
            if (info.counts.containsKey(Kind.OBJECT_ID) && info.counts.size() == 1)
            {
                return 24;
            }
            return Math.max(255, Math.min(MAX_STRING_LENGTH, info.maxStringLength));
        }
        return 0;
    }

    private static String nativeType(PathInfo info, int sampleCount)
    {
        StringBuilder summary = new StringBuilder("MongoDB ");
        boolean first = true;
        for (Map.Entry<Kind, Integer> entry : info.counts.entrySet())
        {
            if (!first)
            {
                summary.append(", ");
            }
            int percent = (int) Math.round(entry.getValue() * 100.0 / Math.max(1, sampleCount));
            summary.append(entry.getKey().label).append(' ').append(percent).append('%');
            first = false;
        }
        summary.append(" (sample ").append(sampleCount).append(')');
        return summary.toString();
    }

    private static boolean isAddressable(String path)
    {
        if (path == null || path.isBlank() || path.contains("..") || path.startsWith(".")
            || path.endsWith(".") || "*".equals(path))
        {
            return false;
        }
        if ((path.startsWith("'") && path.endsWith("'")) || path.matches("\\d+(\\.\\d+)?"))
        {
            return false;
        }
        return path.split("\\.").length <= 10;
    }

    private static String sanitize(String path)
    {
        String name = path.replace(".", "__").replaceAll("[^A-Za-z0-9_$]", "_");
        if (name.isEmpty() || !Character.isLetter(name.charAt(0)) && name.charAt(0) != '_')
        {
            name = "_" + name;
        }
        return name.length() > 120 ? name.substring(0, 120) : name;
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

    private enum Kind
    {
        STRING("string"),
        INT32("int32"),
        INT64("int64"),
        BIGINT("int64"),
        DOUBLE("double"),
        DECIMAL("decimal128"),
        BOOLEAN("bool"),
        DATE("date"),
        OBJECT_ID("objectId"),
        DOCUMENT("document"),
        ARRAY("array"),
        BINARY("binary"),
        NULL("null"),
        OTHER("unknown"),
        TEXT("mixed"),
        MIXED("mixed");

        private final String label;

        Kind(String label)
        {
            this.label = label;
        }
    }

    private static final class PathInfo
    {
        private final Map<Kind, Integer> counts = new LinkedHashMap<>();
        private boolean documentSeen;
        private boolean nonDocumentSeen;
        private boolean hasChildren;
        private boolean depthTruncated;
        private int depth = Integer.MAX_VALUE;
        private int maxStringLength;
    }
}
