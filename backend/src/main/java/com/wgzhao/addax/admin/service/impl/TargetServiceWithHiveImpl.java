package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.HiveConnectDto;
import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wgzhao.addax.admin.common.Constants.DELETED_PLACEHOLDER_PREFIX;

@Service
@Slf4j
public class TargetServiceWithHiveImpl
        implements TargetService {

    @Autowired
    private DictService dictService;

    @Autowired
    private ColumnService columnService;

    @Autowired
    private EtlJourService jourService;

    @Autowired
    private SystemConfigService configService;

    private volatile DataSource hiveDataSource;

    private static final int HIVE_DECIMAL_MAX_PRECISION = 38;
    private static final int HIVE_DECIMAL_MAX_SCALE = 10;

    @Override
    public Connection getHiveConnect() {
        if (hiveDataSource == null) {
            synchronized (this) {
                if (hiveDataSource == null) {
                    HiveConnectDto hiveConnectDto = configService.getHiveServer2();
                    log.info("try to load hive jdbc driver from {}", hiveConnectDto.driverPath());
                    try {
                        hiveDataSource = getHiveDataSourceWithConfig(hiveConnectDto);
                        return hiveDataSource.getConnection();
                    } catch (SQLException | MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        try {
            return hiveDataSource.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get connection from Hive DataSource", e);
        }
    }

    @Override
    public DataSource getHiveDataSourceWithConfig(HiveConnectDto hiveConnectDto)
            throws MalformedURLException {

        File hiveJarFile = new File(hiveConnectDto.driverPath());
        URL[] jarUrls = new URL[]{hiveJarFile.toURI().toURL()};
        // 创建独立的类加载器
        URLClassLoader classLoader = new URLClassLoader(jarUrls, this.getClass().getClassLoader());

        // Set the context class loader
        Thread.currentThread().setContextClassLoader(classLoader);

        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(hiveConnectDto.url());
        dataSource.setUsername(hiveConnectDto.username());
        dataSource.setPassword(hiveConnectDto.password());
        dataSource.setDriverClassName(hiveConnectDto.driverClassName());
        return dataSource;
    }

    /**
     * 为指定 Hive 表添加分区。
     *
     * @param taskId    采集任务ID
     * @param db        Hive数据库名
     * @param table     Hive表名
     * @param partName  分区字段名
     * @param partValue 分区字段值
     * @return 是否添加成功
     */
    @Override
    public boolean addPartition(long taskId, String db, String table, String partName, String partValue) {
        String sql = String.format("ALTER TABLE `%s`.`%s` ADD IF NOT EXISTS PARTITION (%s='%s')", db, table, partName, partValue);
        EtlJour etlJour = jourService.addJour(taskId, JourKind.PARTITION, sql);
        try (Connection conn = getHiveConnect();
             Statement stmt = conn.createStatement()) {
            log.info("Add partition for {}.{}: {}", db, table, sql);
            stmt.execute(sql);
            jourService.successJour(etlJour);
            return true;
        } catch (SQLException e) {
            log.error("Failed to add partition ", e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    /**
     * 创建或更新 Hive 目标表。
     * 包括建库、建表、分区、表属性等操作。
     *
     * @param etlTable 采集表视图对象
     * @return 是否创建/更新成功
     */
    @Override
    public boolean createOrUpdateHiveTable(VwEtlTableWithSource etlTable) {
        List<String> hiveColumns = columnService.getHiveColumnsAsDDL(etlTable.getId());

        String createDbSql = MessageFormat.format("create database if not exists `{0}` location ''{1}/{0}''",
                etlTable.getTargetDb(), dictService.getHdfsPrefix());
        String createTableSql = MessageFormat.format("""
                        create external table if not exists `{0}`.`{1}` (
                        {2}
                        ) comment ''{3}''
                        partitioned by ( `{4}` string )
                         stored as {5}
                         location ''{6}/{0}/{1}''
                         tblproperties (''external.table.purge''=''true'', ''discover.partitions''=''true'', ''orc.compress''=''{7}'', ''snappy.compress''=''{7}'')
                        """,
                etlTable.getTargetDb(), etlTable.getTargetTable(), String.join(",\n", hiveColumns), etlTable.getTblComment(),
                etlTable.getPartName(), dictService.getHdfsStorageFormat(), dictService.getHdfsPrefix(), dictService.getHdfsCompress());

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
            List<EtlColumn> desiredCols = columnService.getColumns(etlTable.getId());
            // build desired active map name -> EtlColumn (skip deleted placeholders)
            LinkedHashMap<String, EtlColumn> desiredActive = new LinkedHashMap<>();
            for (EtlColumn c : desiredCols) {
                String name = c.getColumnName();
                if (name != null && !name.startsWith(DELETED_PLACEHOLDER_PREFIX)) {
                    desiredActive.put(name, c);
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
                } else {
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
                } catch (SQLException ex) {
                    log.error("Failed to apply alter for {}.{}: {}", etlTable.getTargetDb(), etlTable.getTargetTable(), ddl, ex);
                    jourService.failJour(j, ex.getMessage());
                    // continue trying next statements; optionally could break depending on config
                }
            }
            return true;
        } catch (SQLException e) {
            log.warn("Failed to create or update hive table ({}.{}) ", etlTable.getTargetDb(), etlTable.getTargetTable(), e);
            jourService.failJour(etlJour, e.getMessage());
            return false;
        }
    }

    private static String normalizeComment(String v) {
        if (v == null) return "";
        String c = v.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return c.replace("'", "''");
    }

    // minimal column struct from DESCRIBE output
    private static class HiveCol {
        String name;
        String type;
        String comment;

        HiveCol(String n, String t, String c) {
            this.name = n;
            this.type = t;
            this.comment = c;
        }
    }

    /**
     * Parse DESCRIBE `db`.`table` output to current columns map (non-partition columns only).
     */
    private LinkedHashMap<String, HiveCol> describeHiveColumns(Statement stmt, String db, String table) throws SQLException {
        LinkedHashMap<String, HiveCol> cols = new LinkedHashMap<>();
        String sql = String.format("DESCRIBE `%s`.`%s`", db, table);
        try (var rs = stmt.executeQuery(sql)) {
            boolean inCols = true;
            while (rs.next()) {
                String col = rs.getString(1);
                String type = rs.getString(2);
                String comment = rs.getString(3);
                if (col == null) continue;
                col = col.trim();
                if (col.isEmpty()) {
                    // blank line separates columns from partition columns in DESCRIBE
                    inCols = false;
                    continue;
                }
                if (!inCols) {
                    // skip partition section
                    continue;
                }
                // skip headers like # col_name
                if (col.startsWith("#")) continue;
                cols.put(col, new HiveCol(col, type == null ? "" : type.trim(), comment == null ? "" : comment.trim()));
            }
        }
        return cols;
    }

    /**
     * Determine whether current Hive column type is compatible (equal or more relaxed) than desired type.
     * If compatible, we skip changing the column type.
     */
    private static boolean isHiveTypeCompatible(String currentTypeRaw, String desiredTypeRaw) {
        if (currentTypeRaw == null || desiredTypeRaw == null) {
            return false;
        }
        HiveType current = HiveType.parse(currentTypeRaw);
        HiveType desired = HiveType.parse(desiredTypeRaw);

        // Exact match
        if (current.equals(desired)) {
            return true;
        }

        // Special rule: if desired is DECIMAL, only compatible when current is DECIMAL or STRING
        if (desired.base == HiveType.Base.DECIMAL) {
            return current.base == HiveType.Base.DECIMAL || current.base == HiveType.Base.STRING;
        }

        // Strings are most relaxed and can hold anything
        if (current.base == HiveType.Base.STRING) {
            return true;
        }
        // Binary is also very relaxed for arbitrary bytes
        if (current.base == HiveType.Base.BINARY && desired.isPrimitive()) {
            return true;
        }

        // Numeric hierarchy: DOUBLE > FLOAT > DECIMAL(p,s) > BIGINT > INT > SMALLINT > TINYINT
        if (current.base.isNumeric() && desired.base.isNumeric()) {
            return isNumericWiderOrEqual(current, desired);
        }

        // CHAR/VARCHAR compatibility
        if (current.base == HiveType.Base.VARCHAR || current.base == HiveType.Base.CHAR) {
            // string desired always compatible
            if (desired.base == HiveType.Base.STRING) return true;
            if (desired.base == HiveType.Base.VARCHAR || desired.base == HiveType.Base.CHAR) {
                int curLen = current.param1OrDefault(65535); // assume large defaults
                int desLen = desired.param1OrDefault(0);
                return curLen >= desLen;
            }
            // numeric to varchar/char shouldn't be forced; treat current varchar/char as relaxed
            if (desired.base.isNumeric()) return true;
        }

        // DATE/TIMESTAMP: timestamp can represent date
        if (current.base == HiveType.Base.TIMESTAMP && desired.base == HiveType.Base.DATE) {
            return true;
        }
        if (current.base == HiveType.Base.TIMESTAMP && desired.base == HiveType.Base.TIMESTAMP) {
            return true;
        }
        if (current.base == HiveType.Base.DATE && desired.base == HiveType.Base.DATE) {
            return true;
        }

        // Boolean: only compatible with boolean
        if (current.base == HiveType.Base.BOOLEAN) {
            return desired.base == HiveType.Base.BOOLEAN;
        }

        // For complex types or mismatched primitives, default to not compatible
        return false;
    }

    private static boolean isNumericWiderOrEqual(HiveType cur, HiveType des) {
        // Clamp DECIMAL precision/scale to Hive's max supported before any comparisons
        // This ensures comparisons are made within Hive capabilities (max (38,10))
        if (cur.base == HiveType.Base.DECIMAL) {
            int cp = cur.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int cs = cur.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            cp = Math.min(cp, HIVE_DECIMAL_MAX_PRECISION);
            cs = Math.min(cs, HIVE_DECIMAL_MAX_SCALE);
            cur = new HiveType(HiveType.Base.DECIMAL, cp, cs, cur.raw);
        }
        if (des.base == HiveType.Base.DECIMAL) {
            int dp = des.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int ds = des.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            dp = Math.min(dp, HIVE_DECIMAL_MAX_PRECISION);
            ds = Math.min(ds, HIVE_DECIMAL_MAX_SCALE);
            des = new HiveType(HiveType.Base.DECIMAL, dp, ds, des.raw);
        }

        // Handle decimal precision/scale
        if (cur.base == HiveType.Base.DECIMAL && des.base == HiveType.Base.DECIMAL) {
            int cp = cur.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int cs = cur.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            int dp = des.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int ds = des.param2OrDefault(HIVE_DECIMAL_MAX_SCALE);
            return cp >= dp && cs >= ds;
        }
        // Decimal can hold integers/floats depending on precision/scale; treat decimal as between float and bigint depending on scale
        if (cur.base == HiveType.Base.DECIMAL && (des.base == HiveType.Base.BIGINT || des.base == HiveType.Base.INT || des.base == HiveType.Base.SMALLINT || des.base == HiveType.Base.TINYINT)) {
            // integer desired: decimal with scale 0 and precision high enough is compatible; if unknown, assume compatible
            int cs = cur.param2OrDefault(0);
            int cp = cur.param1OrDefault(HIVE_DECIMAL_MAX_PRECISION);
            int needPrecision = switch (des.base) {
                case BIGINT -> 19; // up to 9223372036854775807
                case INT -> 10;
                case SMALLINT -> 5;
                case TINYINT -> 3;
                default -> 0;
            };
            return cs == 0 && cp >= needPrecision;
        }
        if (cur.base == HiveType.Base.DECIMAL && (des.base == HiveType.Base.FLOAT || des.base == HiveType.Base.DOUBLE)) {
            // decimal to float/double: float/double are wider; decimal is not wider for arbitrary floats
            return false;
        }
        if ((cur.base == HiveType.Base.FLOAT || cur.base == HiveType.Base.DOUBLE) && des.base == HiveType.Base.DECIMAL) {
            // float/double are wider than decimal
            return true;
        }

        int curRank = cur.base.numericRank();
        int desRank = des.base.numericRank();
        return curRank >= desRank; // higher rank means wider
    }

    /** Lightweight Hive type struct */
    private static class HiveType {
        enum Base {
            STRING, VARCHAR, CHAR, BINARY,
            BOOLEAN,
            TINYINT, SMALLINT, INT, BIGINT,
            FLOAT, DOUBLE,
            DECIMAL,
            DATE, TIMESTAMP,
            // others: complex
            ARRAY, MAP, STRUCT, UNION, UNKNOWN;

            boolean isNumeric() {
                return this == TINYINT || this == SMALLINT || this == INT || this == BIGINT || this == FLOAT || this == DOUBLE || this == DECIMAL;
            }

            int numericRank() {
                // smaller -> lower rank; wider -> higher rank
                return switch (this) {
                    case TINYINT -> 1;
                    case SMALLINT -> 2;
                    case INT -> 3;
                    case BIGINT -> 4;
                    case DECIMAL -> 5; // treat decimal as wider than BIGINT for integers
                    case FLOAT -> 6;
                    case DOUBLE -> 7;
                    default -> -1;
                };
            }
        }

        final Base base;
        final Integer param1; // e.g., length or precision
        final Integer param2; // e.g., scale
        final String raw;

        HiveType(Base base, Integer p1, Integer p2, String raw) {
            this.base = base;
            this.param1 = p1;
            this.param2 = p2;
            this.raw = raw;
        }

        boolean isPrimitive() {
            return base != Base.ARRAY && base != Base.MAP && base != Base.STRUCT && base != Base.UNION && base != Base.UNKNOWN;
        }

        int param1OrDefault(int def) { return param1 == null ? def : param1; }
        int param2OrDefault(int def) { return param2 == null ? def : param2; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HiveType)) return false;
            HiveType other = (HiveType) o;
            return Objects.equals(normalizeBase(this.base), normalizeBase(other.base)) &&
                    Objects.equals(this.param1, other.param1) &&
                    Objects.equals(this.param2, other.param2);
        }

        private static Base normalizeBase(Base b) {
            // treat STRING/VARCHAR/CHAR distinct for equality, but compatibility handles relaxation
            return b;
        }

        static HiveType parse(String raw) {
            String s = raw.trim().toLowerCase();
            // strip extra spaces
            s = s.replaceAll("\\s+", " ");
            // patterns
            Pattern decimal = Pattern.compile("decimal\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
            Pattern varchar = Pattern.compile("varchar\\s*\\(\\s*(\\d+)\\s*\\)");
            Pattern charp = Pattern.compile("char\\s*\\(\\s*(\\d+)\\s*\\)");

            Matcher m;
            if ((m = decimal.matcher(s)).matches()) {
                int p = Integer.parseInt(m.group(1));
                int sc = Integer.parseInt(m.group(2));
                // Clamp parsed DECIMAL to Hive max to avoid out-of-range comparisons later
                p = Math.min(p, HIVE_DECIMAL_MAX_PRECISION);
                sc = Math.min(sc, HIVE_DECIMAL_MAX_SCALE);
                return new HiveType(Base.DECIMAL, p, sc, raw);
            }
            if ((m = varchar.matcher(s)).matches()) {
                return new HiveType(Base.VARCHAR, Integer.parseInt(m.group(1)), null, raw);
            }
            if ((m = charp.matcher(s)).matches()) {
                return new HiveType(Base.CHAR, Integer.parseInt(m.group(1)), null, raw);
            }
            // simple types
            return switch (s) {
                case "string" -> new HiveType(Base.STRING, null, null, raw);
                case "binary" -> new HiveType(Base.BINARY, null, null, raw);
                case "boolean" -> new HiveType(Base.BOOLEAN, null, null, raw);
                case "tinyint" -> new HiveType(Base.TINYINT, null, null, raw);
                case "smallint" -> new HiveType(Base.SMALLINT, null, null, raw);
                case "int", "integer" -> new HiveType(Base.INT, null, null, raw);
                case "bigint" -> new HiveType(Base.BIGINT, null, null, raw);
                case "float" -> new HiveType(Base.FLOAT, null, null, raw);
                case "double" -> new HiveType(Base.DOUBLE, null, null, raw);
                case "date" -> new HiveType(Base.DATE, null, null, raw);
                case "timestamp" -> new HiveType(Base.TIMESTAMP, null, null, raw);
                default -> {
                    // detect complex types roughly
                    if (s.startsWith("array<")) yield new HiveType(Base.ARRAY, null, null, raw);
                    if (s.startsWith("map<")) yield new HiveType(Base.MAP, null, null, raw);
                    if (s.startsWith("struct<")) yield new HiveType(Base.STRUCT, null, null, raw);
                    if (s.startsWith("uniontype<")) yield new HiveType(Base.UNION, null, null, raw);
                    yield new HiveType(Base.UNKNOWN, null, null, raw);
                }
            };
        }
    }

    /**
     * Check whether hive table exists by SHOW TABLES IN db LIKE 'table'
     */
    private boolean hiveTableExists(Statement stmt, String db, String table)  {
        String sql = String.format("SHOW TABLES IN `%s` LIKE '%s'", db, table);
        try (var rs = stmt.executeQuery(sql)) {
            return rs.next();
        } catch (SQLException e) {
            log.error("Failed to check hive table existence for {}.{} ", db, table, e);
            return false;
        }
    }
}
