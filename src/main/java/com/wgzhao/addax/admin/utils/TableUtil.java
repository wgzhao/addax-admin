package com.wgzhao.addax.admin.utils;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

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

import static cn.hutool.json.JSONUtil.readJSON;

/**
 * execute special table copy task
 */
@Slf4j
public class TableUtil {

    private static String getColumns(ResultSetMetaData rsmd) {
        StringJoiner joiner = new StringJoiner(",");
        try {
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                joiner.add(rsmd.getColumnLabel(i));
            }
            return joiner.toString();
        } catch (SQLException e) {
            log.error("getColumns error", e);
            return null;
        }
    }

    private static boolean copyRecords(JSON json) {
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
        log.info("Connect source db with: {}", srcJdbc);
        if (srcJdbc.contains("clickhouse")) {
            try {
                Class.forName("ru.yandex.clickhouse.ClickHouseDriver");
            } catch (ClassNotFoundException e) {
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
                log.info("OK");
            }

            String insertSql = "insert into " + destTable;
            StringJoiner joinerv = new StringJoiner(",");
            StringJoiner joinerc = new StringJoiner(",");

            String query;

            if (!Objects.equals(null, json.getByPath("src.dbtable", String.class)) && !"".equals(json.getByPath("src.dbtable"))) {
                query = "select * from " + json.getByPath("src.dbtable", String.class);
            } else {
                query = json.getByPath("src.sql", String.class);
            }

            log.info("Retrieves source records");
            srcStmt.setFetchSize(256);
            srcStmt.execute(query);
            log.info("OK");
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

            int batchSize = 0;
            log.info("Begin insert records");
            while (resSet.next()) {
                for (int i = 1; i <= colNum; i++) {
                    if ("unknown".equals(resMd.getColumnTypeName(i))) {
                        preparedStmt.setObject(i, resSet.getObject(i), Types.VARCHAR);
                    } else {
                        preparedStmt.setObject(i, resSet.getObject(i), resMd.getColumnType(i));
                    }
                }
                preparedStmt.addBatch();
                batchSize++;
                if (batchSize % 256 == 0) {
                    preparedStmt.executeBatch();
                    destConn.commit();
                    preparedStmt.clearBatch();
                }
            }
            preparedStmt.executeBatch();
            destConn.commit();
            log.info("OK");
            if (postSql != null && !Objects.requireNonNull(postSql).trim().isEmpty()) {
                log.info("Execute post-sql on dest db: {}", postSql);
                destStmt.execute(postSql);
                destConn.commit();
                log.info("OK");
            }
            destConn.close();
            srcConn.close();
            return true;
        } catch (SQLException e) {
            log.error("Connect source db failed", e);
            return false;
        }
    }

    public static boolean tableCopy(JSON job) {
        return copyRecords(job);
    }

    public static boolean tableCopy(String job) {
        return copyRecords(JSONUtil.parseObj(job));
    }
}