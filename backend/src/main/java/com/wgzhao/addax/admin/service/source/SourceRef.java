package com.wgzhao.addax.admin.service.source;

import tools.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;

/**
 * Immutable view of a source endpoint. Both the JPA entity and the collection view can produce one,
 * so the adapters never need to know which shape the caller had.
 */
public record SourceRef(
    Integer id,
    String dbType,
    String url,
    String username,
    String password,
    JsonNode connectConfig
)
{
    public static SourceRef of(EtlSource source)
    {
        if (source == null)
        {
            return null;
        }
        return new SourceRef(source.getId(), source.getDbType(), source.getUrl(),
            source.getUsername(), source.getPass(), source.getConnectConfig());
    }

    public static SourceRef of(VwEtlTableWithSource table)
    {
        if (table == null)
        {
            return null;
        }
        return new SourceRef(table.getSid(), table.getDbType(), table.getUrl(),
            table.getUsername(), table.getPass(), table.getConnectConfig());
    }
}
