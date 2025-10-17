
-- addax_admin.users definition

CREATE TABLE `users` (
  `username` varchar(50) NOT NULL,
  `password` varchar(500) NOT NULL,
  `enabled` int(11) NOT NULL,
  PRIMARY KEY (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.authorities definition

CREATE TABLE `authorities` (
  `username` varchar(50) NOT NULL,
  `authority` varchar(50) NOT NULL,
  UNIQUE KEY `username` (`username`,`authority`),
  CONSTRAINT `authorities_ibfk_1` FOREIGN KEY (`username`) REFERENCES `users` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.`groups` definition

CREATE TABLE `groups` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `group_name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.group_members definition

CREATE TABLE `group_members` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `group_id` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `group_id` (`group_id`),
  CONSTRAINT `group_members_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.group_authorities definition

CREATE TABLE `group_authorities` (
  `group_id` int(11) NOT NULL,
  `authority` varchar(50) NOT NULL,
  KEY `group_id` (`group_id`),
  CONSTRAINT `group_authorities_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.etl_source definition

CREATE TABLE `etl_source` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(10) NOT NULL,
  `name` varchar(200) NOT NULL,
  `url` varchar(500) NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  `pass` varchar(64) DEFAULT NULL,
  `start_at` time DEFAULT NULL,
  `prerequisite` varchar(4000) DEFAULT NULL,
  `pre_script` varchar(4000) DEFAULT NULL,
  `remark` varchar(2000) DEFAULT NULL,
  `enabled` int(11) DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.etl_table definition

CREATE TABLE `etl_table` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `source_db` varchar(32) NOT NULL,
  `source_table` varchar(64) NOT NULL,
  `target_db` varchar(50) NOT NULL,
  `target_table` varchar(200) NOT NULL,
  `part_kind` char(1) DEFAULT 'D',
  `part_name` varchar(20) DEFAULT 'logdate',
  `filter` varchar(2000) NOT NULL DEFAULT '1=1',
  `kind` char(1) DEFAULT 'A',
  `retry_cnt` int(11) DEFAULT '3',
  `start_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `end_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `max_runtime` int(11) DEFAULT '2000',
  `sid` int(11) NOT NULL,
  `duration` int(11) NOT NULL DEFAULT '0',
  `part_format` varchar(10) DEFAULT 'yyyyMMdd',
  `storage_format` varchar(10) NOT NULL DEFAULT 'orc',
  `compress_format` varchar(10) NOT NULL DEFAULT 'snappy',
  `tbl_comment` varchar(500) DEFAULT NULL,
  `status` char(1) NOT NULL DEFAULT 'U',
  PRIMARY KEY (`id`),
  KEY `sid` (`sid`),
  CONSTRAINT `etl_table_ibfk_1` FOREIGN KEY (`sid`) REFERENCES `etl_source` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.etl_column definition

CREATE TABLE `etl_column` (
  `tid` int(11) NOT NULL,
  `column_name` varchar(255) DEFAULT NULL,
  `column_id` int(11) DEFAULT NULL,
  `source_type` varchar(64) DEFAULT NULL,
  `data_length` int(11) DEFAULT NULL,
  `data_precision` int(11) DEFAULT NULL,
  `data_scale` int(11) DEFAULT NULL,
  `col_comment` varchar(4000) DEFAULT NULL,
  `target_type` varchar(50) NOT NULL,
  `target_type_full` varchar(100) DEFAULT NULL,
  `update_at` datetime DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `tid` (`tid`,`column_name`),
  CONSTRAINT `etl_column_ibfk_1` FOREIGN KEY (`tid`) REFERENCES `etl_table` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.etl_soutab definition

CREATE TABLE `etl_soutab` (
  `sou_db_conn` varchar(64) NOT NULL,
  `owner` varchar(64) NOT NULL,
  `table_name` varchar(64) NOT NULL,
  `column_name` varchar(64) NOT NULL,
  `data_type` varchar(64) DEFAULT NULL,
  `data_length` int(11) DEFAULT NULL,
  `data_precision` int(11) DEFAULT NULL,
  `data_scale` int(11) DEFAULT NULL,
  `column_id` int(11) DEFAULT NULL,
  `table_type` varchar(32) DEFAULT NULL,
  `tab_comment` varchar(2000) DEFAULT NULL,
  `col_comment` varchar(2000) DEFAULT NULL,
  `dw_clt_date` datetime DEFAULT CURRENT_TIMESTAMP,
  `tid` varchar(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.etl_statistic definition

CREATE TABLE `etl_statistic` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tid` int(11) DEFAULT NULL,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `take_secs` int(11) DEFAULT NULL,
  `total_bytes` int(11) DEFAULT NULL,
  `byte_speed` int(11) DEFAULT NULL,
  `rec_speed` int(11) DEFAULT NULL,
  `total_recs` int(11) DEFAULT NULL,
  `total_errors` int(11) DEFAULT NULL,
  `run_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_sta_tid` (`tid`,`run_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.notification definition

CREATE TABLE `notification` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `phone` varchar(255) NOT NULL,
  `msg` varchar(500) NOT NULL,
  `sms` char(1) NOT NULL DEFAULT 'Y',
  `im` char(1) NOT NULL DEFAULT 'Y',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.sys_dict definition

CREATE TABLE `sys_dict` (
  `code` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `classification` varchar(2000) DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.sys_item definition

CREATE TABLE `sys_item` (
  `dict_code` int(11) NOT NULL,
  `item_key` varchar(255) NOT NULL,
  `item_value` varchar(2000) DEFAULT NULL,
  `remark` varchar(4000) DEFAULT NULL,
   UNIQUE KEY `sys_item_dict_code_IDX` (`dict_code`,`item_key`) USING BTREE,
  CONSTRAINT `sys_item_ibfk_1` FOREIGN KEY (`dict_code`) REFERENCES `sys_dict` (`code`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.addax_log definition

CREATE TABLE `addax_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tid` int(11) NOT NULL,
  `run_at` datetime DEFAULT NULL,
  `run_date` date DEFAULT NULL,
  `log` text,
  PRIMARY KEY (`id`),
  KEY `idx_tid_run_date` (`tid`,`run_at`),
  CONSTRAINT `addax_log_ibfk_1` FOREIGN KEY (`tid`) REFERENCES `etl_table` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- addax_admin.etl_job definition

CREATE TABLE `etl_job` (
  `tid` int(11) NOT NULL,
  `job` text NOT NULL,
  KEY `tid` (`tid`),
  CONSTRAINT `etl_job_ibfk_1` FOREIGN KEY (`tid`) REFERENCES `etl_table` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


-- addax_admin.etl_jour definition

CREATE TABLE `etl_jour` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `tid` int(11) DEFAULT NULL,
  `kind` varchar(32) DEFAULT NULL,
  `start_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `duration` int(11) NOT NULL DEFAULT '0',
  `status` int(11) DEFAULT '1',
  `cmd` text,
  `error_msg` varchar(4000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_etl_jour_tid` (`tid`),
  CONSTRAINT `etl_jour_ibfk_1` FOREIGN KEY (`tid`) REFERENCES `etl_table` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;