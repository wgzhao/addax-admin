package com.wgzhao.addax.admin.repository.hive;

import com.wgzhao.addax.admin.dto.HiveTableColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HiveTableColumnRepository extends JpaRepository<Object, Long>
{
    @Query(value = """
            select
            	`t`.`db_id` AS `db_id`,
            	`t`.`db_name` AS `db_name`,
            	`t`.`db_location` AS `db_location`,
            	`t`.`tbl_id` AS `tbl_id`,
            	`t`.`tbl_name` AS `tbl_name`,
            	`t`.`tbl_type` AS `tbl_type`,
            	`t`.`tbl_location` AS `tbl_location`,
            	`t`.`cd_id` AS `cd_id`,
            	`t`.`tbl_comment` AS `tbl_comment`,
            	`c`.`COLUMN_NAME` AS `col_name`,
            	`c`.`TYPE_NAME` AS `col_type`,
            	`c`.`comment` AS `col_comment`,
            	(`c`.`INTEGER_IDX` + 1) AS `col_idx`
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
            	`t`.`tbl_comment` AS `tbl_comment`,
            	`c`.`PKEY_NAME` AS `pkey_name`,
            	`c`.`PKEY_TYPE` AS `pkey_type`,
            	`c`.`PKEY_COMMENT` AS `pkey_comment`,
            	(`c`.`INTEGER_IDX` + 1000) AS `c.integer_idx+1000`
            from
            	(`vw_tab_cols_base` `t`
            join `PARTITION_KEYS` `c` on
            	((`c`.`TBL_ID` = `t`.`tbl_id`)))
            """, nativeQuery = true)
    List<HiveTableColumn> findAllHiveTableColumn();
}
