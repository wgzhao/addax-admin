package com.wgzhao.addax.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TargetOptions;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.dto.TargetTableMetadata;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTargetRepo;
import com.wgzhao.addax.admin.service.ColumnService;
import com.wgzhao.addax.admin.service.ColumnMappingService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.RiskLogService;
import com.wgzhao.addax.admin.service.target.TargetAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import jakarta.annotation.PreDestroy;

import static com.wgzhao.addax.admin.common.Constants.DEFAULT_PART_FORMAT;
import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;
import static com.wgzhao.addax.admin.common.HiveType.isHiveTypeCompatible;

@Service
@Slf4j
@RequiredArgsConstructor
public class TargetServiceWithHiveImpl
    implements TargetAdapter
{

    private final DictService dictService;
    private final ColumnService columnService;
    private final ColumnMappingService columnMappingService;
    private final EtlJourService jourService;
    private final SystemConfigService configService;
    private final RiskLogService riskLogService;
    private final StringRedisTemplate redisTemplate;
    private final EtlTargetRepo etlTargetRepo;

    private volatile DataSource hiveDataSource;
    // keep a reference to the registered driver shim so we can deregister it if we ever reinit
    private volatile Driver registeredHiveDriver;
    // classloader holding the hive driver jar; must be closed when driver is re-initialized or on destroy
    private volatile URLClassLoader hiveClassLoader;

    @Override
    public String getType()
    {
        return "HDFS";
    }

    @Override
    public boolean hasDateDimension(VwEtlTableWithSource table)
    {
        return table != null && TargetOptions.hdfsPartitionBindings(table.getTargetOptions()).stream()
            .anyMatch(binding -> "BIZ_DATE".equalsIgnoreCase(binding.valueType()));
    }

    private static String normalizeComment(String v)
    {
        if (v == null) {
            return "";
        }
        String c = v.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return c.replace("'", "''");
    }

    public Connection getHiveConnect()
    {
        if (hiveDataSource == null) {
            synchronized (this) {
                if (hiveDataSource == null) {
                    HiveConnectDto hiveConnectDto = configService.getHiveServer2();
                    log.info("try to load hive jdbc driver from {}", hiveConnectDto.driverPath());
                    try {
                        hiveDataSource = getHiveDataSourceWithConfig(hiveConnectDto);
                        Connection conn = hiveDataSource.getConnection();
                        if (conn != null) {
                            try {
                                var md = conn.getMetaData();
                                log.info("Obtained Hive connection: driver={} url={}", md.getDriverName(), md.getURL());
                            }
                            catch (Throwable ignore) {
                                // ignore metadata logging failures
                            }
                        }
                        return conn;
                    }
                    catch (SQLException | MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        try {
            return hiveDataSource.getConnection();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to get connection from Hive DataSource", e);
        }
    }

    public DataSource getHiveDataSourceWithConfig(HiveConnectDto hiveConnectDto)
        throws MalformedURLException
    {

        File hiveJarFile = new File(hiveConnectDto.driverPath());
        if (!hiveJarFile.exists() || !hiveJarFile.isFile()) {
            String msg = String.format("Hive driver jar not found at path: %s", hiveConnectDto.driverPath());
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        URL[] jarUrls = new URL[] {hiveJarFile.toURI().toURL()};
        // close previous classloader to release JAR file handles and allow GC of old driver classes
        if (hiveClassLoader != null) {
            try {
                hiveClassLoader.close();
            }
            catch (IOException ex) {
                log.warn("Failed to close previous Hive URLClassLoader", ex);
            }
        }
        // 创建独立的类加载器
        URLClassLoader classLoader = new URLClassLoader(jarUrls, this.getClass().getClassLoader());
        this.hiveClassLoader = classLoader;

        // We'll set the context class loader only for the period of loading/registering the driver
        ClassLoader previousCl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);

            // Explicitly load and instantiate the driver class from the custom classloader
            try {
                Class<?> drvClass = Class.forName(hiveConnectDto.driverClassName(), true, classLoader);
                Object drvObj = drvClass.getDeclaredConstructor().newInstance();
                if (!(drvObj instanceof Driver realDriver)) {
                    String msg = String.format("Configured driver class %s is not a java.sql.Driver", hiveConnectDto.driverClassName());
                    log.error(msg);
                    throw new IllegalArgumentException(msg);
                }

                // If there's already a registered shim from previous init, deregister it first
                try {
                    if (registeredHiveDriver != null) {
                        try {
                            DriverManager.deregisterDriver(registeredHiveDriver);
                        }
                        catch (SQLException ex) {
                            log.warn("Failed to deregister previous hive driver shim", ex);
                        }
                        registeredHiveDriver = null;
                    }
                }
                catch (Exception ex) {
                    log.debug("No previous hive driver to deregister", ex);
                }

                Driver shim = new DriverShim(realDriver);
                try {
                    DriverManager.registerDriver(shim);
                    registeredHiveDriver = shim;
                    log.info("Registered Hive driver shim for {}", hiveConnectDto.driverClassName());
                }
                catch (SQLException se) {
                    String msg = String.format("Failed to register hive driver shim: %s", se.getMessage());
                    log.error(msg, se);
                    throw new IllegalArgumentException(msg, se);
                }
            }
            catch (ClassNotFoundException cnfe) {
                String msg = String.format("Hive driver class not found: %s", hiveConnectDto.driverClassName());
                log.error(msg, cnfe);
                throw new IllegalArgumentException(msg, cnfe);
            }
            catch (ReflectiveOperationException roe) {
                String msg = String.format("Failed to instantiate hive driver class: %s", hiveConnectDto.driverClassName());
                log.error(msg, roe);
                throw new IllegalArgumentException(msg, roe);
            }
        }
        finally {
            // restore previous classloader to avoid side effects for other parts of the application
            Thread.currentThread().setContextClassLoader(previousCl);
        }

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(hiveConnectDto.url());
        dataSource.setUsername(hiveConnectDto.username());
        dataSource.setPassword(hiveConnectDto.password());
        // Do not set driverClassName here: we loaded the driver via a custom classloader
        // and registered a DriverShim with DriverManager. Let DriverManager find the
        // registered driver for the URL to avoid ClassNotFoundException from
        // commons-dbcp2 trying to load the driver via the app classloader.
        return dataSource;
    }

    /**
     * 为指定 Hive 表添加分区。
     * 对于非分区表（partName 为空），直接返回 true，不执行任何操作。
     *
     * @param taskId 采集任务 ID
     * @param db Hive 数据库名
     * @param table Hive 表名
     * @param partName 分区字段名（为空表示非分区表)
     * @param partValue 分区字段值
     * @return 是否添加成功
     */
    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue)
    {
        // 检查是否为非分区表
        if (partName == null || partName.trim().isEmpty()) {
            log.info("Table {}.{} is non-partitioned, skip partition operation", db, table);
            return true;
        }

        String sql = String.format("ALTER TABLE `%s`.`%s` ADD IF NOT EXISTS PARTITION (%s='%s')", db, table, partName, partValue);
        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
        try (Connection conn = getHiveConnect();
            Statement stmt = conn.createStatement()) {
            log.info("Add partition for {}.{}: {}", db, table, sql);
            stmt.execute(sql);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e) {
            log.error("Failed to add partition ", e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    private List<PartitionValue> resolvePartitionValues(VwEtlTableWithSource table, LocalDate targetDate)
    {
        List<PartitionValue> values = new ArrayList<>();
        for (TargetOptions.HdfsPartitionBinding binding :
            TargetOptions.hdfsPartitionBindings(table.getTargetOptions()))
        {
            String value;
            if ("BIZ_DATE".equalsIgnoreCase(binding.valueType()))
            {
                String format = StringUtils.defaultIfBlank(binding.format(), DEFAULT_PART_FORMAT);
                value = targetDate.format(DateTimeFormatter.ofPattern(format));
            }
            else if ("CONSTANT".equalsIgnoreCase(binding.valueType()))
            {
                value = StringUtils.defaultString(binding.value());
            }
            else
            {
                throw new IllegalArgumentException("Unsupported partition valueType: " + binding.valueType());
            }
            values.add(new PartitionValue(binding.name(), value));
        }
        return values;
    }

    private boolean addPartitions(long taskId, VwEtlTableWithSource table, List<PartitionValue> partitions)
    {
        String spec = partitions.stream()
            .map(partition -> "`" + partition.name().replace("`", "``") + "`='"
                + partition.value().replace("'", "''") + "'")
            .collect(java.util.stream.Collectors.joining(", "));
        String sql = String.format("ALTER TABLE `%s`.`%s` ADD IF NOT EXISTS PARTITION (%s)",
            table.getTargetDb(), table.getTargetTable(), spec);
        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
        try (Connection connection = getHiveConnect();
            Statement statement = connection.createStatement())
        {
            statement.execute(sql);
            jourService.successJour(etlJour);
            return true;
        }
        catch (SQLException e)
        {
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    private boolean partitionExists(long taskId, VwEtlTableWithSource table, List<PartitionValue> partitions)
    {
        String expected = partitions.stream()
            .map(partition -> partition.name() + "=" + partition.value())
            .collect(java.util.stream.Collectors.joining("/"));
        String sql = String.format("SHOW PARTITIONS `%s`.`%s`", table.getTargetDb(), table.getTargetTable());
        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
        try (Connection connection = getHiveConnect();
            Statement statement = connection.createStatement();
            var rs = statement.executeQuery(sql))
        {
            while (rs.next())
            {
                if (expected.equalsIgnoreCase(rs.getString(1))) {
                    jourService.successJour(etlJour);
                    return true;
                }
            }
            jourService.failJour(etlJour, "分区不存在: " + expected);
            return false;
        }
        catch (SQLException e)
        {
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    @Override
    public TargetTableMetadata inspectTable(VwEtlTableWithSource etlTable)
    {
        if (etlTable == null || etlTable.getTargetDb() == null || etlTable.getTargetDb().isBlank()
            || etlTable.getTargetTable() == null || etlTable.getTargetTable().isBlank())
        {
            throw new IllegalArgumentException("targetDb and targetTable are required");
        }
        try (Connection connection = getHiveConnect();
            Statement statement = connection.createStatement())
        {
            if (!hiveTableExists(statement, etlTable.getTargetDb(), etlTable.getTargetTable()))
            {
                return TargetTableMetadata.missing();
            }
            HiveDescription description = describeHiveTable(
                statement, etlTable.getTargetDb(), etlTable.getTargetTable());
            List<TargetTableMetadata.TargetColumn> columns = new ArrayList<>();
            int ordinal = 0;
            for (HiveCol column : description.columns().values())
            {
                columns.add(new TargetTableMetadata.TargetColumn(
                    column.name, column.type, 0, 0, true, null, false, ++ordinal));
            }
            return new TargetTableMetadata(true, columns, description.partitions(), Set.of(), Set.of());
        }
        catch (SQLException e)
        {
            throw new RuntimeException("读取 Hive 目标表元数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean prepareBeforeRun(long taskId, VwEtlTableWithSource table, LocalDate bizDate)
    {
        if (table == null) {
            return false;
        }
        List<PartitionValue> partitions = resolvePartitionValues(table, bizDate);
        if (partitions.isEmpty())
        {
            return true;
        }
        if ("EXTERNAL".equalsIgnoreCase(table.getPartitionCreationMode()))
        {
            return partitionExists(taskId, table, partitions);
        }
        return addPartitions(taskId, table, partitions);
    }

    @Override
    public String buildWriterJob(VwEtlTableWithSource table)
    {
        return buildWriterJobForDate(table, configService.getBizDateAsDate());
    }

    @Override
    public String buildWriterJobForDate(VwEtlTableWithSource table, LocalDate targetDate)
    {
        String template = resolveWriterTemplate(table);
        Map<String, String> values = new HashMap<>();
        // A row may not carry storage/compression in its bag; fall back to the global HDFS settings, which
        // is what the table DDL already uses, so the writer and the table it writes to cannot disagree.
        values.put("compress", StringUtils.defaultIfBlank(table.getCompressFormat(), dictService.getHdfsCompress()));
        values.put("fileType", StringUtils.defaultIfBlank(table.getStorageFormat(), dictService.getHdfsStorageFormat()));
        values.put("writeMode", table.getWriteMode());
        values.put("targetDb", table.getTargetDb());
        values.put("targetTable", table.getTargetTable());
        fillConnectionPlaceholders(values, table);
        values.put("column", resolveColumnPlaceholder(template, table));
        values.putAll(configService.getBizDateValuesForDate(targetDate));

        Path hdfsPath = Paths.get(configService.getHdfsPrefix(), table.getTargetDb(), table.getTargetTable());
        for (PartitionValue partition : resolvePartitionValues(table, targetDate))
        {
            hdfsPath = hdfsPath.resolve(partition.name() + "=" + partition.value());
        }
        values.put("path", hdfsPath.toString());

        return new org.apache.commons.text.StringSubstitutor(values).replace(template);
    }

    private String resolveWriterTemplate(VwEtlTableWithSource table)
    {
        if (table != null && table.getTargetId() != null) {
            String key = etlTargetRepo.findById(table.getTargetId())
                .map(t -> t.getWriterTemplateKey())
                .orElse(null);
            if (key != null && !key.isBlank()) {
                String tpl = dictService.getItemValue(5001, key, String.class);
                if (tpl != null && !tpl.isBlank()) {
                    return tpl;
                }
            }
        }
        return configService.getHdfsWriterTemplate();
    }

    private void fillConnectionPlaceholders(Map<String, String> values, VwEtlTableWithSource table)
    {
        if (table == null || table.getTargetId() == null) {
            return;
        }
        etlTargetRepo.findById(table.getTargetId()).ifPresent(target -> {
            String config = target.getConnectConfig();
            if (config == null || config.isBlank()) {
                return;
            }
            try {
                JsonNode node = new ObjectMapper().readTree(config);
                putIfPresent(values, "jdbcUrl", node, "url");
                putIfPresent(values, "username", node, "username");
                putIfPresent(values, "password", node, "password");
            }
            catch (Exception e) {
                log.warn("Invalid connect_config for target {}, ignore placeholders: {}", table.getTargetId(), e.getMessage());
            }
        });
    }

    private void putIfPresent(Map<String, String> values, String key, JsonNode node, String nodeKey)
    {
        JsonNode n = node.get(nodeKey);
        if (n != null && !n.isNull()) {
            String v = n.asText();
            if (v != null && !v.isBlank()) {
                values.put(key, v);
            }
        }
    }

    /**
     * 创建或更新 Hive 目标表。
     * 包括建库、建表、分区、表属性等操作。
     * 支持分区表和非分区表：当 partName 为空时创建非分区表。
     *
     * @param etlTable 采集表视图对象
     * @return 是否创建/更新成功
     */
    @Override
    public boolean createOrUpdateTable(VwEtlTableWithSource etlTable)
    {
        List<String> hiveColumns = columnService.getHiveColumnsAsDDL(etlTable.getId());

        String createDbSql = MessageFormat.format("create database if not exists `{0}` location ''{1}/{0}''",
            etlTable.getTargetDb(), dictService.getHdfsPrefix());

        List<TargetOptions.HdfsPartitionBinding> bindings =
            TargetOptions.hdfsPartitionBindings(etlTable.getTargetOptions());
        boolean isPartitioned = !bindings.isEmpty();
        String partitionDdl = bindings.stream()
            .map(binding -> "`" + binding.name().replace("`", "``") + "` string")
            .collect(java.util.stream.Collectors.joining(", "));

        String createTableSql;
        if (isPartitioned) {
            // 分区表
            createTableSql = MessageFormat.format("""
                    create external table if not exists `{0}`.`{1}` (
                    {2}
                    ) comment ''{3}''
                    partitioned by ( {4} )
                     stored as {5}
                     location ''{6}/{0}/{1}''
                     tblproperties (''external.table.purge''=''true'', ''discover.partitions''=''true'', ''orc.compress''=''{7}'', ''snappy.compress''=''{7}'')
                    """,
                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(),
                partitionDdl, dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), dictService.getHdfsCompress());
        }
        else {
            // 非分区表
            createTableSql = MessageFormat.format("""
                    create external table if not exists `{0}`.`{1}` (
                    {2}
                    ) comment ''{3}''
                     stored as {4}
                     location ''{5}/{0}/{1}''
                     tblproperties (''external.table.purge''=''true'', ''orc.compress''=''{6}'', ''snappy.compress''=''{6}'')
                    """,
                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(),
                dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), dictService.getHdfsCompress());
        }

        log.info("create table sql:\n{}", createTableSql);
        EtlJour etlJour = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, createTableSql);
        try (Connection conn = getHiveConnect();
            Statement stmt = conn.createStatement()) {
            stmt.execute(createDbSql);
            // 1) Check whether table exists before creation
            boolean existedBefore = hiveTableExists(stmt, etlTable.getTargetDb(), etlTable.getTargetTable());
            // Ensure DB and table exist (no-op if already there)
            stmt.execute(createTableSql);
            jourService.successJour(etlJour);

            // If table did not exist before, skip ALTER/diff logic on first creation
            if (!existedBefore) {
                log.info("Table {}.{} was newly created; skip alter diff on initial creation.", etlTable.getTargetDb(), etlTable.getTargetTable());
                return true;
            }

            // 2) ALTER mode: fetch current hive columns and apply diffs
            // build desired active map name -> EtlColumn (skip deleted placeholders)
            LinkedHashMap<String, EtlColumn> desiredActive = new LinkedHashMap<>();
            for (ColumnMappingService.ResolvedColumnMapping mapping : columnMappingService.resolve(etlTable.getId())) {
                String name = mapping.targetColumnName();
                if (name != null && !name.isBlank() && mapping.sourceColumn() != null) {
                    desiredActive.put(name, mapping.sourceColumn());
                }
            }
            // fetch current hive columns via DESCRIBE
            LinkedHashMap<String, HiveCol> hiveCurrent = describeHiveColumns(stmt, etlTable.getTargetDb(), etlTable.getTargetTable());

            // generate DDLs
            ArrayList<String> alterDDLs = new ArrayList<>();
            // additions
            for (var entry : desiredActive.entrySet()) {
                String name = entry.getKey().toLowerCase();
                EtlColumn c = entry.getValue();
                HiveCol hc = hiveCurrent.get(name);
                String comment = normalizeComment(c.getColComment());
                String typeFull = c.getTargetTypeFull();
                String newComment = comment.isEmpty() ? "" : " COMMENT '" + comment + "'";
                if (hc == null) {
                    alterDDLs.add(String.format("ALTER TABLE `%s`.`%s` ADD COLUMNS ( `%s` %s%s )",
                        etlTable.getTargetDb(), etlTable.getTargetTable(), name, typeFull,
                        newComment));
                }
                else {
                    // compare type; if different, use CHANGE COLUMN
                    boolean typeDiff = !Objects.equals(hc.type, typeFull);
                    // only change type when current Hive type is NOT compatible with desired type
                    if (typeDiff && !isHiveTypeCompatible(hc.type, typeFull)) {
                        alterDDLs.add(String.format("ALTER TABLE `%s`.`%s` CHANGE COLUMN `%s` `%s` %s%s",
                            etlTable.getTargetDb(), etlTable.getTargetTable(), name, name, typeFull,
                            newComment));
                    }
                }
            }

            // execute ALTER sequentially
            for (String ddl : alterDDLs) {
                EtlJour j = jourService.addJour(etlTable.getId(), JourKind.UPDATE_TABLE, ddl);
                try {
                    log.info("apply hive alter: {}", ddl);
                    stmt.execute(ddl);
                    jourService.successJour(j);
                }
                catch (SQLException ex) {
                    log.error("Failed to apply alter for {}.{}: {}", etlTable.getTargetDb(), etlTable.getTargetTable(), ddl, ex);
                    jourService.failJour(j, ex.getMessage());
                    // 记录到风险日志表，提示用户后续人工处理
                    try {
                        String msg = String.format("Failed to apply alter %s on %s.%s: %s", ddl, etlTable.getTargetDb(), etlTable.getTargetTable(), ex.getMessage());
                        riskLogService.recordRisk("TargetServiceWithHiveImpl", "ERROR", msg, etlTable.getId());
                    }
                    catch (Exception logEx) {
                        log.warn("Failed to record risk log for alter failure: {}", logEx.getMessage());
                    }
                    // continue trying next statements; optionally could break depending on config
                }
            }
            return true;
        }
        catch (SQLException e) {
            log.warn("Failed to create or update hive table ({}.{}) ", etlTable.getTargetDb(), etlTable.getTargetTable(), e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    /**
     * Parse DESCRIBE `db`.`table` output to current columns map (non-partition columns only).
     */
    private LinkedHashMap<String, HiveCol> describeHiveColumns(Statement stmt, String db, String table)
        throws SQLException
    {
        return describeHiveTable(stmt, db, table).columns();
    }

    private HiveDescription describeHiveTable(Statement stmt, String db, String table)
        throws SQLException
    {
        List<HiveDescriptionRow> rows = new ArrayList<>();
        String sql = String.format("DESCRIBE `%s`.`%s`", db, table);
        try (var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new HiveDescriptionRow(
                    rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        return parseHiveDescription(rows);
    }

    static HiveDescription parseHiveDescription(List<HiveDescriptionRow> rows)
    {
        LinkedHashMap<String, HiveCol> cols = new LinkedHashMap<>();
        List<TargetTableMetadata.TargetPartition> partitions = new ArrayList<>();
        boolean inCols = true;
        for (HiveDescriptionRow row : rows) {
            String col = row.name();
            String type = row.type();
            if (col == null) {
                // Some drivers use a null row for the section separator instead of an empty string.
                if (inCols && type == null && row.comment() == null) {
                    inCols = false;
                }
                continue;
            }
            col = col.trim();
            if (col.isEmpty() || col.equalsIgnoreCase("# Partition Information")) {
                // A blank row separates ordinary columns from partition columns in DESCRIBE.
                inCols = false;
                continue;
            }
            if (!inCols) {
                if (!col.startsWith("#") && type != null && !type.isBlank()) {
                    partitions.add(new TargetTableMetadata.TargetPartition(
                        col, type.trim().toLowerCase()));
                }
                continue;
            }
            // Skip headers like # col_name.
            if (col.startsWith("#")) {
                continue;
            }
            cols.put(col, new HiveCol(
                col, type == null ? "" : type.trim(),
                row.comment() == null ? "" : row.comment().trim()));
        }
        Set<String> partitionNames = new java.util.HashSet<>();
        partitions.forEach(partition ->
            partitionNames.add(partition.name().toLowerCase(java.util.Locale.ROOT)));
        cols.entrySet().removeIf(entry ->
            partitionNames.contains(entry.getKey().toLowerCase(java.util.Locale.ROOT)));
        return new HiveDescription(cols, partitions);
    }

    /**
     * Check whether hive table exists by SHOW TABLES IN db LIKE 'table'
     */
    private boolean hiveTableExists(Statement stmt, String db, String table)
    {
        String sql = String.format("SHOW TABLES IN `%s` LIKE '%s'", db, table);
        try (var rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
        catch (SQLException e) {
            log.error("Failed to check hive table existence for {}.{} ", db, table, e);
            return false;
        }
    }

    public Object getMaxValue(VwEtlTableWithSource table, String columnName, String partValue)
    {
        String datePartition = TargetOptions.hdfsPartitionBindings(table.getTargetOptions()).stream()
            .filter(binding -> "BIZ_DATE".equalsIgnoreCase(binding.valueType()))
            .map(TargetOptions.HdfsPartitionBinding::name)
            .findFirst()
            .orElse(null);
        if (StringUtils.isEmpty(datePartition)) {
            return null;
        }
        String tableName = String.format("`%s`.`%s`", table.getTargetDb(), table.getTargetTable());
        String sql = String.format("SELECT MAX(`%s`) AS max_val FROM %s where %s = '%s'",
            columnName, tableName, datePartition, partValue);
        log.info("getMaxValue sql: {}", sql);

        String redisKey = "target:max:" + table.getId() + ":" + columnName;
        try (Connection conn = getHiveConnect();
            Statement stmt = conn.createStatement();
            var rs = stmt.executeQuery(sql)) {
            Object sqlMax = null;
            if (rs.next()) {
                sqlMax = rs.getObject("max_val");
            }
            if (sqlMax instanceof Number && ((Number) sqlMax).longValue() == 0L) {
                sqlMax = null;
            }
            // If sqlMax is null or zero, try to read from redis cache
            if (sqlMax == null) {
                try {
                    Object cached = redisTemplate.opsForValue().get(redisKey);
                    if (cached != null) {
                       return cached;
                    }
                }
                catch (Exception e) {
                    log.warn("Failed to read max value from redis for key {}: {}", redisKey, e.getMessage());
                }
                // return sqlMax (which may be null or 0)
                return sqlMax;
            }

            // sqlMax is a valid value: update redis and return
            try {
                redisTemplate.opsForValue().set(redisKey, sqlMax.toString());
            }
            catch (Exception e) {
                log.warn("Failed to write max value to redis for key {}: {}", redisKey, e.getMessage());
            }
            return sqlMax;
        }
        catch (SQLException e) {
            log.error("Failed to get max value for {}.{} ", tableName, columnName, e);
            // on SQL failure, try to read redis as a fallback
            try {
                Object cached = redisTemplate.opsForValue().get(redisKey);
                if (cached != null) {
                    return cached;
                }
            }
            catch (Exception ex) {
                log.warn("Failed to read max value from redis for key {} after sql failure: {}", redisKey, ex.getMessage());
            }
            return null;
        }
    }

    private String getHdfsWriteColumns(VwEtlTableWithSource table)
    {
        List<Map<String, String>> columns = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : columnMappingService.resolve(table.getId())) {
            EtlColumn sourceColumn = mapping.sourceColumn();
            if (sourceColumn == null || mapping.targetColumnName() == null) {
                continue;
            }
            Map<String, String> targetColumn = new HashMap<>();
            targetColumn.put("type", sourceColumn.getTargetTypeFull());
            targetColumn.put("name", mapping.targetColumnName());
            columns.add(targetColumn);
        }
        try {
            return new ObjectMapper().writeValueAsString(columns);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("column 转换为 JSON 失败", e);
        }
    }

    private String getRdbmsWriteColumns(VwEtlTableWithSource table)
    {
        List<String> columns = new ArrayList<>();
        for (ColumnMappingService.ResolvedColumnMapping mapping : columnMappingService.resolve(table.getId())) {
            String columnName = mapping.targetColumnName();
            if (columnName == null || columnName.isBlank()) {
                continue;
            }
            columns.add("\"" + columnName + "\"");
        }
        return String.join(", ", columns);
    }

    /**
     * 根据模板中 column 占位符的使用方式自动选择列格式：
     * - 若模板使用 [ ${column} ]，返回 "col1","col2" 这样的列名列表（RDBMS writer 常用）
     * - 否则返回 HDFS writer 所需的对象数组 JSON
     */
    private String resolveColumnPlaceholder(String template, VwEtlTableWithSource table)
    {
        if (template != null && template.matches("(?s).*\\[\\s*\\$\\{column}\\s*].*")) {
            return getRdbmsWriteColumns(table);
        }
        return getHdfsWriteColumns(table);
    }

    // minimal column struct from DESCRIBE output
    private static class HiveCol
    {
        String name;
        String type;
        String comment;

        HiveCol(String n, String t, String c)
        {
            this.name = n;
            this.type = t;
            this.comment = c;
        }
    }

    static record HiveDescription(
        LinkedHashMap<String, HiveCol> columns,
        List<TargetTableMetadata.TargetPartition> partitions
    )
    {
    }

    static record HiveDescriptionRow(String name, String type, String comment)
    {
    }

    private record PartitionValue(String name, String value)
    {
    }

    // Driver shim to wrap a driver loaded from a custom classloader so DriverManager can use it
    private record DriverShim(Driver driver)
        implements Driver
    {

        @Override
        public boolean acceptsURL(String u)
            throws SQLException
        {
            return driver.acceptsURL(u);
        }

        @Override
        public Connection connect(String u, Properties p)
            throws SQLException
        {
            return driver.connect(u, p);
        }

        @Override
        public int getMajorVersion()
        {
            return driver.getMajorVersion();
        }

        @Override
        public int getMinorVersion()
        {
            return driver.getMinorVersion();
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p)
            throws SQLException
        {
            return driver.getPropertyInfo(u, p);
        }

        @Override
        public boolean jdbcCompliant()
        {
            return driver.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger()
            throws SQLFeatureNotSupportedException
        {
            try {
                return driver.getParentLogger();
            }
            catch (AbstractMethodError ame) {
                // some older drivers don't implement this
                throw new SQLFeatureNotSupportedException(ame);
            }
        }
    }

    @PreDestroy
    public void destroy()
    {
        if (registeredHiveDriver != null) {
            try {
                DriverManager.deregisterDriver(registeredHiveDriver);
            }
            catch (SQLException ex) {
                log.warn("Failed to deregister Hive driver on shutdown", ex);
            }
        }
        if (hiveClassLoader != null) {
            try {
                hiveClassLoader.close();
            }
            catch (IOException ex) {
                log.warn("Failed to close Hive URLClassLoader on shutdown", ex);
            }
        }
    }
}
