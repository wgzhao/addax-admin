package com.wgzhao.addax.admin.utils;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class DbUtil
{

    private static String getDriverName(String url)
    {
        // the url must starts with 'jdbc://' and contains the database name
        if (url.contains("mysql")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        else if (url.contains("oracle")) {
            return "oracle.jdbc.driver.OracleDriver";
        }
        else if (url.contains("sqlserver")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        else if (url.contains("postgresql")) {
            return "org.postgresql.Driver";
        }
        else if (url.contains("db2")) {
            return "com.ibm.db2.jcc.DB2Driver";
        }
        else if (url.contains("sqlite")) {
            return "org.sqlite.JDBC";
        }
        else if (url.contains("h2")) {
            return "org.h2.Driver";
        }
        else if (url.contains("derby")) {
            return "org.apache.derby.jdbc.ClientDriver";
        }
        else if (url.contains("clickhouse") || url.contains("chk")) {
            return "com.clickhouse.ClickHouseDriver";
        }
        else {
            return null;
        }
    }

    // test jdbc is connected or not
    public static boolean testConnection(String url, String username, String password)
    {
        try {
            Class.forName(getDriverName(url));
        }
        catch (ClassNotFoundException e) {
            log.error("Driver not found", e);
            return false;
        }
        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            connection.close();
            return true;
        }
        catch (SQLException e) {
            log.error("Failed to connect database", e);
            return false;
        }
    }

    public static Connection getConnect(String url, String username, String password)
    {
        try {
            Class.forName(getDriverName(url));
            return DriverManager.getConnection(url, username, password);
        }
        catch (ClassNotFoundException | SQLException e) {
            log.error("Failed to connect database", e);
            return null;
        }
    }

    public static Connection getConnect(String url, Properties properties)
    {
        return getConnect(url, properties.getProperty("user", ""), properties.getProperty("password"));
    }

    private static String getColumns(ResultSetMetaData rsmd)
    {
        StringJoiner joiner = new StringJoiner(",");
        try {
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                joiner.add(rsmd.getColumnLabel(i));
            }
            return joiner.toString();
        }
        catch (SQLException e) {
            log.error("getColumns error", e);
            return null;
        }
    }

    private static boolean copyRecords(JSON json)
    {
        Properties srcConnectProps = new Properties();
        Properties destConnectProps = new Properties();

        String srcJdbc = json.getByPath("src.jdbc", String.class);
        String destJdbc = json.getByPath("dest.jdbc", String.class);
        String destTable = json.getByPath("dest.dbtable", String.class);
        String mode = json.getByPath("dest.mode", String.class);
        // presql
        String preSql = json.getByPath("dest.presql", String.class);
        String postSql = json.getByPath("dest.postsql", String.class);
        destConnectProps.put("user", json.getByPath("dest.user", String.class));
        destConnectProps.put("password", json.getByPath("dest.password", String.class));

        srcConnectProps.put("user", json.getByPath("src.user", String.class));
        srcConnectProps.put("password", json.getByPath("src.password", String.class));
        // source database
        log.info("Connecting source db with: {}", srcJdbc);
        if (srcJdbc.contains("clickhouse")) {
            try {
                Class.forName("ru.yandex.clickhouse.ClickHouseDriver");
            }
            catch (ClassNotFoundException e) {
                log.warn("ClassNotFoundException", e);
                return false;
            }
        }

        try (Connection srcConn = DbUtil.getConnect(srcJdbc, srcConnectProps);
                Connection destConn = DbUtil.getConnect(destJdbc, destConnectProps)) {
            if (srcConn == null || destConn == null) {
                return false;
            }
            Statement srcStmt = srcConn.createStatement();
            destConn.setAutoCommit(false);
            Statement destStmt = destConn.createStatement();

            if ("overwrite".equals(mode)) {
                destStmt.execute("truncate table " + json.getByPath("dest.dbtable", String.class));
                destConn.commit();
            }

            if (preSql != null && !Objects.requireNonNull(preSql).trim().isEmpty()) {
                log.info("execute pre-sql on dest db: {}", preSql);
                destStmt.execute(preSql);
            }

            String insertSql = "insert into " + destTable;
            StringJoiner joinerv = new StringJoiner(",");
            StringJoiner joinerc = new StringJoiner(",");

            String query;

            if (!Objects.equals(null, json.getByPath("src.dbtable", String.class)) && !"".equals(json.getByPath("src.dbtable"))) {
                query = "select * from " + json.getByPath("src.dbtable", String.class);
            }
            else {
                query = json.getByPath("src.sql", String.class);
            }

            log.info("Retrieves source records");
            int batchSize = 1000;
            int count = 0;
            srcStmt.setFetchSize(batchSize);
            srcStmt.execute(query);
            ResultSet resSet = srcStmt.getResultSet();
            ResultSetMetaData resMd = resSet.getMetaData();

            // 获得目标表的结构
            Statement stmt = destConn.createStatement();
            String destSql = "select " + getColumns(resMd) + " from " + destTable + " where 1=2";
            log.info("query destination table with SQL: {}", destSql);
            stmt.execute(destSql);
            ResultSet destSchema = stmt.getResultSet();
            ResultSetMetaData destMd = destSchema.getMetaData();

            int colNum = resMd.getColumnCount();
            for (int i = 1; i <= colNum; i++) {
                joinerc.add(destMd.getColumnName(i));
                joinerv.add("?");
            }

            insertSql = insertSql + "(" + joinerc + ")values(" + joinerv + ")";
            PreparedStatement preparedStmt = destConn.prepareStatement(insertSql);

            log.info("Begin insert records");
            while (resSet.next()) {
                for (int i = 1; i <= colNum; i++) {
                    if ("unknown".equals(resMd.getColumnTypeName(i))) {
                        preparedStmt.setObject(i, resSet.getObject(i), Types.VARCHAR);
                    }
                    else {
                        preparedStmt.setObject(i, resSet.getObject(i), resMd.getColumnType(i));
                    }
                }
                preparedStmt.addBatch();
                count++;
                if (count % batchSize == 0) {
                    preparedStmt.executeBatch();
                    destConn.commit();
                    preparedStmt.clearBatch();
                }
            }
            preparedStmt.executeBatch();
            destConn.commit();
            log.info("Insert records done, with {} records", count);
            if (postSql != null && !Objects.requireNonNull(postSql).trim().isEmpty()) {
                log.info("Execute post-sql on dest db: {}", postSql);
                destStmt.execute(postSql);
                destConn.commit();
            }
            destConn.close();
            srcConn.close();
            return true;
        }
        catch (SQLException e) {
            log.error("Connect source db failed", e);
            return false;
        }
    }

    public static boolean tableCopy(JSON job)
    {
        return copyRecords(job);
    }

    public static boolean tableCopy(String job)
    {
        return copyRecords(JSONUtil.parseObj(job));
    }
}
