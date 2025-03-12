package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.utils.CacheUtil;
import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import oracle.ucp.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.io.Serializable;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TaskService
{
    @Resource
    CacheUtil cacheUtil;

    @Autowired
    @Qualifier("oracleDatasource")
    private DataSource dataSource;

    @Resource
    private RedisTemplate<String, Serializable> redisTemplate;

    @Value("${hive.metastore.jdbc.url}")
    private String hiveJdbcUrl;

    @Value("${hive.metastore.jdbc.username}")
    private String hivejdbcUsername;

    @Value("${hive.metastore.jdbc.password}")
    private String hivejdbcPassword;

    @Value("${hive.metastore.jdbc.driver-class-name}")
    private String hivejdbcDriverClassName;

    /**
     * 表字段更新
     */
    public Pair<Boolean, String> tableSchemaUpdate() {
        // step 1. set redis flag
        Boolean acquire = redisTemplate.opsForValue().setIfAbsent("soutab", 1, 3, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquire)) {
            return new Pair<>(false, "soutab is running");
        }
        acquire = redisTemplate.opsForValue().setIfAbsent("soutab.hadoop", 1, 3, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquire)) {
            return new Pair<>(false, "soutab.hadoop is running");
        }

        // step 2. 获取源库及hadoop的表结构信息
        Pair<Boolean, String> pair ;
        if (! copyTableStruct() ) {
            pair = new Pair<>(false, "copy table failed");
        } else {
            pair = new Pair<>(true, "success");
        }
        // step 3. 删除标志
        redisTemplate.delete("soutab.hadoop");
        redisTemplate.delete("soutab");

        return pair;
    }

    @Transactional
    private boolean copyTableStruct() {
        int batchSize = 1000;
        // truncate table
        Connection oracleConnect;
        PreparedStatement preparedStatement;
        try {
            oracleConnect = dataSource.getConnection();
            oracleConnect.createStatement().execute("truncate table TB_IMP_ETL_TBLS_TMP");
            // prepare insert
            String insertSql = "insert into TB_IMP_ETL_TBLS_TMP values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            preparedStatement = oracleConnect.prepareStatement(insertSql);
        }
        catch (SQLException e) {
            log.error("failed to get the oracle connection: ", e);
            return  false;
        }

        try {
            Class.forName(hivejdbcDriverClassName);
            Properties properties = new Properties();
            properties.setProperty("user", hivejdbcUsername);
            properties.setProperty("password", hivejdbcPassword);
            // set timeout
            properties.setProperty("connectTimeout", "10000");
            properties.setProperty("loginTimeout", "5000");
            // set socket time out
            properties.setProperty("socketTimeout", "30000");
            Connection connection = DriverManager.getConnection(hiveJdbcUrl, properties);
            String sql = """
                 select
            	`t`.`db_id` AS `db_id`,
            	`t`.`db_name` AS `db_name`,
            	`t`.`db_location` AS `db_location`,
            	`t`.`tbl_id` AS `tbl_id`,
            	`t`.`tbl_name` AS `tbl_name`,
            	`t`.`tbl_type` AS `tbl_type`,
            	`t`.`tbl_location` AS `tbl_location`,
            	`t`.`cd_id` AS `cd_id`,
            	`c`.`COLUMN_NAME` AS `col_name`,
            	`c`.`TYPE_NAME` AS `col_type`,
            	`c`.`comment` AS `col_comment`,
            	(`c`.`INTEGER_IDX` + 1) AS `col_idx`,
            	`t`.`tbl_comment` AS `tbl_comment`
            from
            	(`vw_tab_cols_base` `t`
            join `COLUMNS_V2` `c` on
            	((`c`.`CD_ID` = `t`.`cd_id`)))
            union all
            select
            	`t`.`db_id` AS `db_id`,
            	`t`.`db_name` AS `db_name`,
            	`t`.`db_location` AS `db_location`,
            	`t`.`tbl_id` AS `tbl_id`,
            	`t`.`tbl_name` AS `tbl_name`,
            	`t`.`tbl_type` AS `tbl_type`,
            	`t`.`tbl_location` AS `tbl_location`,
            	`t`.`cd_id` AS `cd_id`,
            	`c`.`PKEY_NAME` AS `pkey_name`,
            	`c`.`PKEY_TYPE` AS `pkey_type`,
            	`c`.`PKEY_COMMENT` AS `pkey_comment`,
            	(`c`.`INTEGER_IDX` + 1000) AS `c.integer_idx+1000`,
            	`t`.`tbl_comment` AS `tbl_comment`
            from
            	(`vw_tab_cols_base` `t`
            join `PARTITION_KEYS` `c` on
            	((`c`.`TBL_ID` = `t`.`tbl_id`)))
            """;
            ResultSet resultSet = connection.createStatement().executeQuery(sql);
            int count = 0;
            while (resultSet.next()) {
               preparedStatement.setInt(1, resultSet.getInt("db_id"));
                preparedStatement.setString(2, resultSet.getString("db_name"));
                preparedStatement.setString(3, resultSet.getString("db_location"));
                preparedStatement.setInt(4, resultSet.getInt("tbl_id"));
                preparedStatement.setString(5, resultSet.getString("tbl_name"));
                preparedStatement.setString(6, resultSet.getString("tbl_type"));
                preparedStatement.setString(7, resultSet.getString("tbl_location"));
                preparedStatement.setInt(8, resultSet.getInt("cd_id"));
                preparedStatement.setString(9, resultSet.getString("col_name"));
                preparedStatement.setString(10, resultSet.getString("col_type"));
                preparedStatement.setString(11, resultSet.getString("col_comment"));
                preparedStatement.setInt(12, resultSet.getInt("col_idx"));
                preparedStatement.setString(13, resultSet.getString("tbl_comment"));
                preparedStatement.addBatch();
                count++;

                if (count == batchSize) {
                    preparedStatement.executeBatch();
                    preparedStatement.clearBatch();
                    count = 0;
                }
           }
            if (count > 0) {
                preparedStatement.executeBatch();
            }
            preparedStatement.close();
            connection.close();
        }
        catch (SQLException e) {
            log.error("Failed to connect hive metastore database", e);
            return false;
        }
        catch (ClassNotFoundException e) {
            log.error("Failed to load hive driver", e);
            return false;
        }
        // call procedure
        try (CallableStatement callableStatement = oracleConnect.prepareCall("{call sp_imp_alone(?, ?, ?)}")){
            callableStatement.setString(1, "bupdate");
            callableStatement.setString(2, "hadoop");
            callableStatement.setString(3, "n");
            callableStatement.execute();

            callableStatement.close();
        }
        catch (SQLException e) {
            log.error("Failed to prepare call statement", e);
            return false;
        }
        finally {
            try {
                oracleConnect.close();
            }
            catch (SQLException e) {
                log.error("Failed to close the oracle connection", e);
            }
        }
        return true;
    }
}
