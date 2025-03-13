package com.wgzhao.addax.admin.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DbUtil {
    private static final Logger logger = Logger.getLogger("DbUtil");

    private static String getDriverName(String url) {
        // the url must starts with 'jdbc://' and contains the database name
        if (url.contains("mysql")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if (url.contains("oracle")) {
            return "oracle.jdbc.driver.OracleDriver";
        } else if (url.contains("sqlserver")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if (url.contains("postgresql")) {
            return "org.postgresql.Driver";
        } else if (url.contains("db2")) {
            return "com.ibm.db2.jcc.DB2Driver";
        } else if (url.contains("sqlite")) {
            return "org.sqlite.JDBC";
        } else if (url.contains("h2")) {
            return "org.h2.Driver";
        } else if (url.contains("derby")) {
            return "org.apache.derby.jdbc.ClientDriver";
        } else if (url.contains("clickhouse") || url.contains("chk")) {
            return "com.clickhouse.ClickHouseDriver";
        } else {
            return null;
        }
    }
    // test jdbc is connected or not
    public static boolean testConnection(String url, String username, String password) {
        try {
            Class.forName(getDriverName(url));
        } catch (ClassNotFoundException e) {
            logger.severe("Driver not found: " + e.getMessage());
            return false;
        }
        try {
            Connection connection = DriverManager.getConnection(url, username, password);
            connection.close();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            return false;
        }
    }

    public static Connection getConnect(String url, String username, String password) {
        try {
            Class.forName(getDriverName(url));
            return DriverManager.getConnection(url, username, password);
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            return null;
        }
    }

    public static Connection getConnect(String url, Properties properties) {
        try {
            Class.forName(getDriverName(url));
            return DriverManager.getConnection(url, properties.getProperty("user", ""), properties.getProperty("password"));
        } catch (ClassNotFoundException | SQLException e) {
            logger.log(Level.SEVERE, e.getMessage());
            return null;
        }
    }


}
