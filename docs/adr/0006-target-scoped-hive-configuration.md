# Scope Hive connections and HDFS writer defaults to the target endpoint

The platform started as an HDFS/Hive-only collector: one HiveServer2 connection, one HDFS prefix and one storage/compression pair were global system settings. Once relational targets became first-class, those globals stopped describing the deployment: two HDFS targets may point at different HiveServer2 instances or write under different prefixes, and a deployment that only uses MySQL should not have to configure a Hive driver at all.

We therefore treat the HDFS/Hive settings as properties of the target endpoint (`etl_target.connect_config`):

- `url`, `username`, `password` — the HiveServer2 connection used for DDL, partitions, metadata and `MAX()` lookups;
- `defaultFS`, `hdfsPrefix`, `hdfsSitePath`, `enableKerberos`, `hadoopConfig` — the writer's cluster identity;
- `storageFormat`, `compressFormat` — per-target defaults that a table may override through `etl_table.target_options.hdfs`.

## Consequences

Hive JDBC drivers follow the same rule as every RDBMS driver: their jars live in `${APP_HOME}/drivers` (`-Daddax.drivers.dir`). The whole directory is loaded in a child classloader and exposed through a single `DriverManager` shim, so a vendor "standalone" jar that bundles its own Tomcat/SLF4J classes can be dropped there without breaking application startup; a broken driver only fails the URLs that need it. A per-target `driverPath` remains as an optional escape hatch for a self-contained jar outside the directory. The driver is only needed by addax-admin for metadata work — Addax's own `hdfswriter` does not use it. A missing driver is reported as a 400 with the expected directory, not as a 500.

The legacy `sys_item` rows (`HIVE_SERVER2`, `HDFS_PREFIX`, `HDFS_STORAGE_FORMAT`, `HDFS_COMPRESS_FORMAT`, `HDFS_CONFIG`) are copied into each HDFS target once at startup, filling only missing keys. The rows are kept for one release so rolling back the application still finds its configuration, but the API and UI no longer expose them.

A rendered writer job is authoritative over a customised `wH` template for the target-scoped fields: after substitution the adapter overwrites `defaultFS`, `hdfsSitePath`, `haveKerberos`, `hadoopConfig`, `fileType`, `compress` and `path`. A template that still contains an old cluster name can therefore never write to it again.

HDFS is no longer the implicit fallback. A table without a target, or a `target_id` that no longer resolves, raises a configuration error; a missing default target is reported instead of silently routing to HDFS. Target families are exposed by adapters (`TargetFamily`) rather than inferred from the display-only `target_type` string.
