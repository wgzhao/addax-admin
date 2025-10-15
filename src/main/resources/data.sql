insert into users (USERNAME, PASSWORD, ENABLED)
values  ('user', '$2a$10$7pdkeQNqyfj/H.xSqniqDeVNCq8CnXTVNcoP0fgbzFkR53cDgF0z.', true),
        ('admin', '$2a$10$B/24QvXSICyz/qUAe9Va0OWmGKOBA./9HiJBvfHw2QDudDBsGZ43K', true);

insert into authorities (username, authority)
values ('admin', 'admin'), ('user', 'user');

INSERT INTO sys_dict (code,"name",classification,remark) VALUES
	 (1000,'系统配置项开关',NULL,'系统常用配置项开关'),
	 (1011,'短信接收人','FR','统一配送的短信接收人信息'),
	 (1021,'交易日表','FR','自动从柜台取最新的交易日表信息'),
	 (1060,'通用运行状态','FR','通用的运行状态字典E,R,N,Y,W'),
	 (2002,'自动化运行频率','FR','自动化采集及自动化数据服务的运行频率'),
	 (2011,'ODS采集类型映射','FR','源表的字段类型映射至HADOOP'),
	 (2014,'ODS采集特殊字段',NULL,'源表的字段名为关键字的特殊处理(除中文外的字段)'),
	 (2015,'ODS采集增加字段',NULL,'ODS采集时，额外增加的字段'),
	 (5000,'JOB模板','AT','JSON的主模板，与5001关联'),
	 (5001,'JOB模板子模块','AT','实际读写数据库的JSON模块');

INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES
(1000, 'CONCURRENT_LIMIT', '30', '最大采集并发数量'),
(1000, 'HDFS_COMPRESS_FORMAT', 'snappy', '存储 压缩格式，默认 lz4，可选 gz, snappy,zstd'),
(1000, 'HDFS_PREFIX', '/ods', 'HDFS 目录前缀'),
(1000, 'HDFS_STORAGE_FORMAT', 'parquet', '存储格式，默认 ORC，可选 parquet'),
(1000, 'HIVE_CLI', 'hive', 'HIVE 命令行'),
(1000, 'ADDAX', '/opt/app/addax', 'Addax程序目录'),
(1000, 'RUN_LOG', '/var/tmp/log', '运行日志目录');

INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES
(1000, 'QUEUE_SIZE', '100', '采集队列长度'),
(1000, 'SWITCH_TIME', '16:30', '切日时间'),
(1011, '11111111111', '1', '初始化，默认记录'),
(1060, 'E', '运行错误', '2024-13-30'),
(1060, 'N', '未运行', '2024-13-30'),
(1060, 'R', '正在运行', '2024-13-30'),
(1060, 'U', '等待更新表结构', NULL),
(1060, 'W', '等待', '2024-13-30'),
(1060, 'X', '禁用', '2024-13-30'),
(1060, 'Y', '运行结束', '2024-13-30'),
(2002, 'D', '每天', '2024-13-30'),
(2002, 'M', '每月', '2024-13-30'),
(2002, 'N', '全量', '2024-13-30'),
(2002, 'Q', '每季', '2024-13-30'),
(2002, 'W', '每周', '2024-13-30'),
(2002, 'Y', '每年', '2024-13-30'),
(2011, 'bigint', 'bigint', '数值型'),
(2011, 'bit', 'boolean', 'sqlserver的布尔型，jdbc连接时返回的true/false'),
(2011, 'blob', 'string', '字符类型'),
(2011, 'char', 'string', '字符类型'),
(2011, 'character', 'string', '字符类型'),
(2011, 'clob', 'string', '字符类型'),
(2011, 'date', 'string', '日期时间型'),
(2011, 'datetime', 'string', '日期时间型'),
(2011, 'decimal', 'decimal', '数值型'),
(2011, 'double', 'double', '数值型'),
(2011, 'float', 'float', '数值型'),
(2011, 'image', 'binary', '2024-13-30'),
(2011, 'int', 'bigint', '数值型'),
(2011, 'integer', 'bigint', '数值型'),
(2011, 'long', 'string', '字符类型'),
(2011, 'longtext', 'string', '2024-13-30'),
(2011, 'money', 'decimal(19,4)', 'sqlserver的金额类型'),
(2011, 'nchar', 'string', '2024-13-30'),
(2011, 'ntext', 'string', '2024-13-30'),
(2011, 'number', 'decimal', '数值型'),
(2011, 'numeric', 'decimal', '数值型'),
(2011, 'nvarchar', 'string', '字符类型'),
(2011, 'nvarchar2', 'string', '字符类型'),
(2011, 'raw', 'string', '字符类型'),
(2011, 'rowid', 'string', '字符类型'),
(2011, 'smalldatetime', 'string', '日期时间型'),
(2011, 'smallint', 'int', 'mysql的SMALLINT'),
(2011, 'string', 'string', '2024-13-30'),
(2011, 'text', 'string', '字符类型'),
(2011, 'time', 'string', '字符类型'),
(2011, 'timestamp', 'string', '日期时间型'),
(2011, 'tinyint', 'int', 'mysql的TINYINT'),
(2011, 'varchar', 'string', '字符类型'),
(2011, 'varchar2', 'string', '字符类型'),
(2014, 'AFTER', NULL, '2024-13-30'),
(2014, 'BEFORE', NULL, '2024-13-30'),
(2014, 'CHANGE', NULL, '2024-13-30'),
(2014, 'COMMENT', NULL, '2024-13-30'),
(2014, 'CONDITION', NULL, '2024-13-30'),
(2014, 'COUNT', NULL, '2024-13-30'),
(2014, 'CREATE', NULL, '2024-13-30'),
(2014, 'CURRENT_USER', NULL, '2024-13-30'),
(2014, 'DATE', NULL, '2024-13-30'),
(2014, 'DEFAULT', NULL, '2024-13-30'),
(2014, 'DESC', NULL, '2024-13-30'),
(2014, 'DESCRIBE', NULL, '2024-13-30'),
(2014, 'EXPLAIN', NULL, NULL),
(2014, 'FROM', NULL, '2024-13-30'),
(2014, 'FUNCTION', NULL, '2024-13-30'),
(2014, 'GROUP', NULL, '2024-13-30'),
(2014, 'GROUPS', NULL, '2024-13-30'),
(2014, 'INDEX', NULL, '2024-13-30'),
(2014, 'INTERVAL', NULL, '2024-13-30'),
(2014, 'KEY', NULL, '2024-13-30'),
(2014, 'LEVEL', NULL, '2024-13-30'),
(2014, 'LIMIT', NULL, '2024-13-30'),
(2014, 'ORDER', NULL, '2024-13-30'),
(2014, 'RANGE', NULL, '2024-13-30'),
(2014, 'RANK', NULL, '2024-13-30'),
(2014, 'REPEAT', NULL, '2024-13-30'),
(2014, 'SHOW', NULL, '2024-13-30'),
(2014, 'SQL', NULL, '2024-13-30'),
(2014, 'TABLE', NULL, '2024-13-30'),
(2014, 'TYPE', NULL, '2024-13-30'),
(2014, 'USE', NULL, '2024-13-30'),
(2014, 'USING', NULL, '2024-13-30'),
(2015, 'dw_clt_date', 'string', '采集表的具体时间(精确到秒)'),
(2015, 'dw_trade_date', 'decimal(10,0)', '数据采集时的交易日期(8位)'),
(2015, 'modifier_no', 'string', '备用,一般标注为系统编号');

INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'D2H', '{
  "job": {
    "content": [
    {
      ${rD},
      ${wH}
    }],
    "setting": {
      "speed": {
        "batchSize": 20480,
        "bytes": -1,
        "channel": 1
      }
    }
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'H2C', '{
 "job": {
  "setting": {
   "speed": {
    "byte": -1,
    "channel": 1,
    "batchSize": 200
   },
   "errorLimit": {
    "record": 0,
    "percentage": 0.0
   }
  },
  "content": [
   {
    ${rH},
    ${wC}
   }
  ]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'H2M', '{
 "job": {
  "setting": {
   "speed": {
    "byte": -1,
    "channel": 1,
    "batchSize": 100
   },
   "errorLimit": {
    "record": 0,
    "percentage": 0.0
   }
  },
  "content": [
   {
    ${rH},
    ${wM}
   }
  ]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'H2O', '{
 "job": {
  "setting": {
   "speed": {
    "byte": -1,
    "channel": 1,
    "batchSize": 100
   },
   "errorLimit": {
    "record": 0,
    "percentage": 0.0
   }
  },
  "content": [
   {
    ${rH},
    ${wO}
   }
  ]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'H2P', '{
 "job": {
  "setting": {
   "speed": {
    "byte": -1,
    "channel": 1,
    "batchSize": 200
   },
   "errorLimit": {
    "record": 0,
    "percentage": 0.0
   }
  },
  "content": [
   {
    ${rH},
    ${wP}
   }
  ]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'H2S', '{
 "job": {
  "setting": {
   "speed": {
    "byte": -1,
    "channel": 1,
    "batchSize": 200
   },
   "errorLimit": {
    "record": 0,
    "percentage": 0.0
   }
  },
  "content": [
   {
    ${rH},
    ${wS}
   }
  ]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'I2H', '{
  "job": {
    "setting": {
      "speed": {
        "byte": -1,
        "channel": 1
      },
      "errorLimit": {
        "record": 0,
        "percentage": 0
      }
    },
    "content": [
      {
        ${rI},
        ${wH}
      }
    ]
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'M2H', '{
  "job": {
    "content": [
    {
      ${rM},
      ${wH}
    }],
    "setting": {
      "speed": {
        "batchSize": 20480,
        "bytes": -1,
        "channel": 1
      }
    }
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'O2H', '{
  "job": {
    "content": [
    {
      ${rO},
      ${wH}
    }],
    "setting": {
      "speed": {
        "batchSize": 20480,
        "bytes": -1,
        "channel": 1
      }
    }
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'P2H', '{
  "job": {
    "content": [
    {
      ${rP},
      ${wH}
    }],
    "setting": {
      "speed": {
        "batchSize": 20480,
        "bytes": -1,
        "channel": 1
      }
    }
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5000, 'S2H', '{
  "job": {
    "content": [
    {
      ${rS},
      ${wH}
    }],
    "setting": {
      "speed": {
        "batchSize": 20480,
        "bytes": -1,
        "channel": 1
      }
    }
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rD', '"reader": {
 "name": "dbffilereader",
 "parameter": {
  "column": [${col}],
  "path": "/opt/infalog/trans/zdbs/${TD}/${sou_tblname}100007.dbf",
  "encoding": "GBK"
}}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rH', '"reader": {
 "name": "rdbmsreader",
 "parameter": {
  "username": "${s_user}",
  "password": "${s_pass}",
  "passflag": "false",
  "connection": [{
   "querySql": ["${s_sql}"],
   "jdbcUrl": ["${s_conn}"]
  }]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rI', '"reader": {
   "name": "rdbmsreader",
   "parameter": {
   "username": "${sou_user}",
   "password": "${sou_pass}",
   "column": [ ${sou_col} ],
   "connection": [{
    "table": [ "${sou_tblname}" ],
    "jdbcUrl": [ "${sou_dbcon}" ]
    }],
   "fetchSize": 1024,
   "where": "${sou_filter}"
   }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rM', '"reader": {
 "parameter": {
  "username": "${username}",
  "password": "${pass}",
  "column": [ ${column} ],
  "autoPk": "true",
  "where": "${filter}",
  "connection": [{
   "table": [ "${table_name}" ],
   "jdbcUrl": [ "${url}" ]
  }]
 },
 "name": "mysqlreader"
}
', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rO', '"reader": {
  "parameter": {
    "password": "${sou_pass}",
    "column": [ ${sou_col} ],
    "connection": [{
      "jdbcUrl": ["${sou_dbcon}"],
      "table": ["${sou_tblname}"]
    }],
    "splitPk": "${sou_split}",
    "username": "${sou_user}",
    "where": "${sou_filter}"
  },
  "name": "oraclereader"
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rP', '"reader": {
  "parameter": {
    "password": "${sou_pass}",
    "column": [ ${sou_col} ],
    "connection": [{
      "jdbcUrl": ["${sou_dbcon}"],
      "table": ["${sou_tblname}"]
    }],
    "splitPk": "${sou_split}",
    "username": "${sou_user}",
    "where": "${sou_filter}"
  },
  "name": "postgresqlreader"
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'rS', '"reader": {
  "parameter": {
    "password": "${sou_pass}",
    "column": [ ${sou_col} ],
    "connection": [{
      "jdbcUrl": ["${sou_dbcon}"],
      "table": ["${sou_tblname}"]
    }],
    "splitPk": "${sou_split}",
    "username": "${sou_user}",
    "where": "${sou_filter}"
  },
  "name": "sqlserverreader"
}', 'sqlserver的reader');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'wC', '"writer": {
 "name": "clickhousewriter",
 "parameter": {
  "username": "${d_user}",
  "password": "${d_pass}",
  "passflag": "false",
  "preSql": ["${d_presql}"],
  "postSql": ["${d_postsql}"],
  "column": [ ${d_cols} ],
  "connection": [
   {
    "table": [ "${d_tblname}" ],
    "jdbcUrl": "${d_conn}"
   }
  ]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'wH', '"writer": {
  "name": "hdfswriter",
  "parameter": {
    "path": "${hdfs_path}",
    "fileName": "addax",
    "compress": "lz4",
    "column": [ ${column} ],
    "defaultFS": "hdfs://nameservice1",
    "writeMode": "overwrite",
    "fieldDelimiter": "\u0001",
    "fileType": "orc",
    "haveKerberos": "false",
    "createPath": true,
    "hdfsSitePath": "/opt/app/addax/cdh-hdfs-site.xml"
  }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'wM', '"writer": {
 "name": "mysqlwriter",
 "parameter": {
  "username": "${d_user}",
  "password": "${d_pass}",
  "preSql": ["${d_presql}"],
  "postSql": ["${d_postsql}"],
  "column": [ ${d_cols} ],
  "connection": [{
   "jdbcUrl": "${d_conn}",
   "table": ["${d_tblname}"]
  }]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'wO', '"writer": {
 "name": "oraclewriter",
 "parameter": {
  "username": "${d_user}",
  "password": "${d_pass}",
  "batchSize": 200,
  "preSql": ["${d_presql}"],
  "postSql": ["${d_postsql}"],
  "column": [ ${d_cols} ],
  "connection": [{
   "jdbcUrl": "${d_conn}",
   "table": ["${d_tblname}"]
  }]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'wP', '"writer": {
 "name": "postgresqlwriter",
 "parameter": {
  "username": "${d_user}",
  "password": "${d_pass}",
  "preSql": ["${d_presql}"],
  "postSql": ["${d_postsql}"],
  "column": [ ${d_cols} ],
  "connection": [{
   "jdbcUrl": "${d_conn}",
   "table": ["${d_tblname}"]
  }]
 }
}', '2024-13-30');
INSERT INTO sys_item (dict_code, item_key, item_value, remark) VALUES(5001, 'wS', '"writer": {
 "name": "sqlserverwriter",
 "parameter": {
  "username": "${d_user}",
  "password": "${d_pass}",
  "batchSize": 200,
  "preSql": ["${d_presql}"],
  "postSql": ["${d_postsql}"],
  "column": [ ${d_cols} ],
  "connection": [{
   "jdbcUrl": "${d_conn}",
   "table": ["${d_tblname}"]
  }]
 }
}', '2024-13-30');


CREATE OR REPLACE FUNCTION insert_dates_for_year(p_year INTEGER, p_dict_code INTEGER DEFAULT 1021)
RETURNS VOID AS $$
DECLARE
    v_date DATE;
BEGIN
    FOR v_date IN
        SELECt DISTINCT to_char(date_trunc('day', generate_series(p_year || '-01-01'::date, p_year || '-12-31'::date, '1 day')), 'YYYYmmdd')
        -- SELECT DISTINCT date_trunc('day', generate_series('2025-01-01'::date, '2025-12-31'::date, '1 day'))
    LOOP
        INSERT INTO sys_item (dict_code, item_key, item_value, remark)
        VALUES (p_dict_code, v_date, v_date, NOW());
    END LOOP;
END;
$$ LANGUAGE plpgsql;

insert_dates_for_year(extract(year from now()), 1021);