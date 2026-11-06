package com.wgzhao.addax.admin.service.source;

import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a source endpoint to the adapter that owns its engine.
 *
 * <p>The stored {@code db_type} is authoritative for non-JDBC engines. For historical rows it may be
 * blank or the generic {@code rdbms}; in that case the URL scheme decides. Unknown types fail loudly:
 * silently falling back to RDBMS is what made a {@code mongodb://} URL look like a broken JDBC source.</p>
 */
@Component
public class SourceAdapterRegistry
{
    public static final String RDBMS_TYPE = "RDBMS";
    public static final String MONGODB_TYPE = "MONGODB";
    public static final String MONGODB_DB_TYPE = "mongodb";
    public static final String ELASTICSEARCH_TYPE = "ELASTICSEARCH";
    public static final String ELASTICSEARCH_DB_TYPE = "elasticsearch";

    private final Map<String, SourceAdapter> byType;
    private final boolean nosqlEnabled;

    public SourceAdapterRegistry(List<SourceAdapter> adapters,
        @Value("${addax.source.nosql.enabled:true}") boolean nosqlEnabled)
    {
        Map<String, SourceAdapter> map = new LinkedHashMap<>();
        for (SourceAdapter adapter : adapters)
        {
            map.put(adapter.getType().toUpperCase(Locale.ROOT), adapter);
        }
        this.byType = Map.copyOf(map);
        this.nosqlEnabled = nosqlEnabled;
    }

    public boolean isNosqlEnabled()
    {
        return nosqlEnabled;
    }

    public SourceAdapter resolve(EtlSource source)
    {
        return resolve(SourceRef.of(source));
    }

    public SourceAdapter resolve(VwEtlTableWithSource table)
    {
        return resolve(SourceRef.of(table));
    }

    public SourceAdapter resolve(SourceRef ref)
    {
        if (ref == null)
        {
            throw new ApiException(400, "采集源不存在");
        }
        String explicit = trimLower(ref.dbType());
        String url = ref.url() == null ? "" : ref.url().trim();
        boolean mongoUrl = url.startsWith("mongodb://") || url.startsWith("mongodb+srv://");
        boolean hasMongoConfig = ref.connectConfig() != null && ref.connectConfig().has("address");
        boolean esUrl = url.startsWith("http://") || url.startsWith("https://");
        boolean hasEsConfig = ref.connectConfig() != null && ref.connectConfig().has("endpoints");

        String type;
        if ("mongodb".equals(explicit) || "mongo".equals(explicit))
        {
            type = MONGODB_TYPE;
        }
        else if ("elasticsearch".equals(explicit) || "es".equals(explicit))
        {
            type = ELASTICSEARCH_TYPE;
        }
        else if (explicit.isEmpty() || "rdbms".equals(explicit))
        {
            if (mongoUrl || hasMongoConfig)
            {
                type = MONGODB_TYPE;
            }
            else if (esUrl || hasEsConfig)
            {
                type = ELASTICSEARCH_TYPE;
            }
            else
            {
                type = RDBMS_TYPE;
            }
        }
        else if (DbType.fromValue(explicit).isPresent())
        {
            type = RDBMS_TYPE;
        }
        else
        {
            throw new ApiException(400, "未知的采集源类型: " + ref.dbType());
        }

        if (!RDBMS_TYPE.equals(type) && !nosqlEnabled)
        {
            throw new ApiException(400, "NoSQL 采集源已被禁用（addax.source.nosql.enabled=false）");
        }
        SourceAdapter adapter = byType.get(type);
        if (adapter == null)
        {
            throw new ApiException(400, "采集源类型 " + type + " 没有可用的适配器");
        }
        return adapter;
    }

    public SourceAdapter getByType(String type)
    {
        if (type == null)
        {
            throw new ApiException(400, "采集源类型不能为空");
        }
        SourceAdapter adapter = byType.get(type.trim().toUpperCase(Locale.ROOT));
        if (adapter == null)
        {
            throw new ApiException(400, "未知的采集源类型: " + type);
        }
        return adapter;
    }

    /**
     * Resolve the adapter for a save and let it normalize the entity. Changing the engine of an
     * existing source is rejected: mappings, filters and jobs of the old engine are not portable.
     */
    public SourceAdapter resolveForSave(EtlSource incoming, EtlSource existing)
    {
        SourceAdapter adapter = resolve(SourceRef.of(incoming));
        if (existing != null)
        {
            SourceAdapter previous = resolve(SourceRef.of(existing));
            if (!previous.getType().equals(adapter.getType()))
            {
                throw new ApiException(400, "采集源类型不允许修改");
            }
        }
        adapter.normalize(incoming);
        return adapter;
    }

    public List<SourceDescriptor> descriptors()
    {
        return byType.values().stream()
            .map(SourceAdapter::descriptor)
            .toList();
    }

    private static String trimLower(String value)
    {
        return Optional.ofNullable(value).orElse("").trim().toLowerCase(Locale.ROOT);
    }
}
