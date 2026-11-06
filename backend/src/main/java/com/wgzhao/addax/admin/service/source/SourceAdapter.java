package com.wgzhao.addax.admin.service.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.dto.TableMetaDto;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.collection.IdentifierPolicy;

import java.util.List;

/**
 * Capabilities of one collection source engine.
 *
 * <p>The source side used to be a single JDBC pipeline. Every method here is a seam that pipeline
 * used to hardcode: connection test, namespace/table/column discovery, type mapping, filtering and
 * reader rendering. The RDBMS implementation is a straight extraction of the old code; other engines
 * only implement this interface.</p>
 */
public interface SourceAdapter
{
    /** Stable engine id, e.g. {@code RDBMS} or {@code MONGODB}. */
    String getType();

    SourceDescriptor descriptor();

    SourceCapabilities capabilities();

    /**
     * Write path: validate the incoming connection and set {@code dbType} to the engine's canonical
     * value. RDBMS keeps deriving the concrete JDBC dialect from the URL.
     */
    void normalize(EtlSource source);

    boolean testConnection(SourceRef source);

    IdentifierPolicy identifierPolicy(SourceRef source, String database);

    List<String> listNamespaces(SourceRef source);

    List<TableMetaDto> listTables(SourceRef source, String database);

    SourceSchema inspectSchema(SourceRef source, String database, String table, SchemaMode mode);

    /**
     * Raw source metadata needed by target DDL generation. RDBMS keeps its JDBC probing; other
     * engines must never open a JDBC connection for a URL that is not JDBC.
     */
    SourceTableHints tableHints(SourceRef source, String database, String table);

    ColumnTypeMapping mapColumnType(SourceField field, TargetFamily family,
                                    DbType targetDialect, TypePurpose purpose);

    String defaultFilter();

    String normalizeFilter(SourceRef source, String rawFilter);

    boolean isIncrementalFilter(String rawFilter);

    String incrementalColumn(String rawFilter);

    String buildIncrementalPredicate(VwEtlTableWithSource table,
                                     List<ColumnMappingService.ResolvedColumnMapping> mappings,
                                     String sourceColumn, Object maxValue);

    String combineFilters(String baseFilter, String extraFilter);

    ObjectNode buildReaderJob(VwEtlTableWithSource table, ReaderJobContext context);

    void mergeReaderPluginConfig(ObjectNode readerParameter, JsonNode customConfig,
                                 VwEtlTableWithSource table, java.util.Map<String, String> dateValues);

    String readerPluginName(SourceRef source);
}
