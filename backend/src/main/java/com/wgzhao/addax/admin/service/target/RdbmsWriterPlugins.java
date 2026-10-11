package com.wgzhao.addax.admin.service.target;

import com.wgzhao.addax.admin.common.DbType;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.service.DictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * addax writer plugin mapping for RDBMS targets.
 *
 * Phase 1 only claims the dialects that have both a writer plugin and dialect-correct DDL/type mapping.
 * Everything else fails explicitly instead of emitting a job that references a plugin or DDL syntax we
 * never implemented.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RdbmsWriterPlugins
{
    private static final Map<DbType, String> PLUGIN_BY_DIALECT;

    static
    {
        Map<DbType, String> map = new LinkedHashMap<>();
        map.put(DbType.MYSQL, "mysqlwriter");
        map.put(DbType.POSTGRESQL, "postgresqlwriter");
        map.put(DbType.SQLSERVER, "sqlserverwriter");
        PLUGIN_BY_DIALECT = Collections.unmodifiableMap(map);
    }

    // Report a missing plugin only once per dialect to avoid log spam on every job build.
    private final Set<DbType> warnedMissing = ConcurrentHashMap.newKeySet();

    private final DictService dictService;

    public boolean isSupported(DbType dialect)
    {
        return dialect != null && PLUGIN_BY_DIALECT.containsKey(dialect);
    }

    public Set<DbType> supportedDialects()
    {
        return PLUGIN_BY_DIALECT.keySet();
    }

    public String supportedDialectsText()
    {
        return PLUGIN_BY_DIALECT.keySet().stream().map(Enum::name).collect(Collectors.joining(", "));
    }

    public String pluginName(DbType dialect)
    {
        String plugin = dialect == null ? null : PLUGIN_BY_DIALECT.get(dialect);
        if (plugin == null)
        {
            throw new ApiException(400, "Unsupported RDBMS target dialect '" + dialect
                + "'; supported dialects: " + supportedDialectsText());
        }
        return plugin;
    }

    /**
     * Fail fast while the target is configured, so a missing plugin surfaces at configuration time
     * instead of at collection time.
     */
    public void requireInstalled(DbType dialect)
    {
        String plugin = pluginName(dialect);
        Path home = addaxHome();
        if (home == null || !Files.isDirectory(home))
        {
            // ADDAX_HOME is not visible from here (e.g. a development host); we cannot prove the plugin
            // is missing, so leave it to the run-time warning instead of blocking target configuration.
            return;
        }
        Path dir = home.resolve("plugin").resolve("writer").resolve(plugin);
        if (!Files.isDirectory(dir))
        {
            throw new ApiException(400, "addax writer plugin '" + plugin + "' for dialect " + dialect
                + " is not installed at " + dir + "; install it under ${ADDAX_HOME}/plugin/writer");
        }
    }

    /**
     * Warn (once per dialect) when the mapped writer plugin is missing under ADDAX_HOME. The
     * configuration-time check is authoritative; this only covers jobs built before it ran.
     */
    public void warnIfMissing(DbType dialect)
    {
        try
        {
            String plugin = PLUGIN_BY_DIALECT.get(dialect);
            Path dir = plugin == null ? null : pluginDir(plugin);
            if (dir == null || Files.isDirectory(dir) || !warnedMissing.add(dialect))
            {
                return;
            }
            log.warn("addax writer plugin '{}' is mapped for dialect {} but not found at {}. "
                + "Collection jobs will fail until the plugin is installed.", plugin, dialect, dir);
        }
        catch (Exception e)
        {
            log.debug("Failed to check addax writer plugin dir: {}", e.getMessage());
        }
    }

    private Path pluginDir(String plugin)
    {
        Path home = addaxHome();
        return home == null ? null : home.resolve("plugin").resolve("writer").resolve(plugin);
    }

    private Path addaxHome()
    {
        try
        {
            String home = dictService.getAddaxHome();
            if (home == null || home.isBlank())
            {
                return null;
            }
            return Path.of(home);
        }
        catch (Exception e)
        {
            log.debug("Failed to resolve ADDAX_HOME: {}", e.getMessage());
            return null;
        }
    }
}
