-- admin:admin123
-- user:user123
insert into PUBLIC.users (USERNAME, PASSWORD, ENABLED)
values  ('user', '$2a$10$7pdkeQNqyfj/H.xSqniqDeVNCq8CnXTVNcoP0fgbzFkR53cDgF0z.', true),
        ('admin', '$2a$10$g9Zr25jZp/GcGhkxICRlMO0uHRkNc8FoZQ.r04PJS74yDCOSTDg/K', true);

insert into public.authorities (username, authority)
values  ('admin', 'admin'),
        ('user', 'user');

INSERT INTO public.sys_dict (code, name, classification, remark) VALUES
(1000, '系统配置项开关', null, '系统常用配置项开关'),
(1011, '短信接收人', 'FR', '统一配送的短信接收人信息'),
(1021, '交易日表', 'FR', '自动从柜台取最新的交易日表信息'),
(1060, '通用运行状态', 'FR', '通用的运行状态字典E,R,N,Y,W'),
(2011, 'ODS采集类型映射', 'FR', '源表的字段类型映射至HADOOP'),
(2015, 'ODS采集增加字段', null, 'ODS采集时，额外增加的字段'),
(5000, 'JOB模板', 'AT', 'JSON的主模板，与5001关联'),
(5001, 'JOB模板子模块', 'AT', '实际读写数据库的JSON模块'),
(2010, 'SQL 保留字', null, 'SQL 的关键字，当字段使用了关键字时，需要对其转义');


insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (1000, 'HDFS_PREFIX', '/ods', 'HDFS 目录前缀'),
        (1000, 'HIVE_CLI', 'hive', 'HIVE 命令行'),
        (1000, 'RUN_LOG', '/var/tmp/log', '运行日志目录'),
        (1000, 'CONCURRENT_LIMIT', '30', '最大采集并发数量'),
        (1000, 'ADDAX', '/opt/app/addax', 'Addax程序目录'),
        (1000, 'QUEUE_SIZE', '40', '采集队列长度'),
        (1000, 'SWITCH_TIME', '16:20', '切日时间'),
        (1000, 'HDFS_COMPRESS_FORMAT', 'lz4', '存储 压缩格式，默认 lz4，可选 gz, snappy,zstd'),
        (1000, 'HDFS_STORAGE_FORMAT', 'orc', '存储格式，默认 ORC，可选 parquet'),
        (1000, 'SCHEMA_REFRESH_TIMEOUT', '600', '表结构刷新超时秒数'),
        (1000, 'HDFS_CONFIG', '{"defaultFS":"hdfs://cluster","hdfsPrefix":"/ods/","enableKerberos":false,"enableHA":true,"hdfsSitePath":"/etc/hadoop/conf/hdfs-site.xml"}', 'HDFS 配置'),
        (1000, 'HIVE_SERVER2', '{"url":"jdbc:hive2://nn01:10000/default","username":"hive","password":"","driverClassName":"org.apache.hive.jdbc.HiveDriver","driverPath":"/usr/hdp/current/hive-client/jdbc/hive-jdbc-3.1.0.3.1.4.0-315-standalone.jar"}', null);


insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (1011, '11111111111', '1', '初始化，默认记录');

insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (1060, 'E', '运行错误', '2024-13-30');

insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (1060, 'N', '未运行', '2024-13-30'),
        (1060, 'R', '正在运行', '2024-13-30'),
        (1060, 'U', '等待更新表结构', null),
        (1060, 'W', '等待', '2024-13-30'),
        (1060, 'X', '禁用', '2024-13-30'),
        (1060, 'Y', '运行结束', '2024-13-30');

insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (2011, 'bigint', 'bigint', '数值型'),
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
        (2011, 'bigint unsigned', 'bigint', '无符号整数'),
        (2011, 'tinyint unsigned', 'int', '无符号整形'),
        (2011, 'smallint unsigned', 'int', '无符号整形'),
        (2011, 'mediumint unsigned', 'int', '无符号整形'),
        (2011, 'int unsigned', 'int', '无符号整形');

insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (2015, 'dw_clt_date', 'string', '采集表的具体时间(精确到秒)'),
        (2015, 'dw_trade_date', 'decimal(10,0)', '数据采集时的交易日期(8位)'),
        (2015, 'modifier_no', 'string', '备用,一般标注为系统编号');

insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (5001, 'rR', '{
    "name": "${name}",
    "parameter": {
        "username": "${username}",
        "password": "${password}",
        "column": [${column}],
        "where": "${where}",
        "autoPk": "${autoPk}",
        "splitPk": "${splitPk}",
        "connection": {
            "jdbcUrl": [ "${jdbcUrl}" ],
            "table": [ "${table}" ]
        },
        "fetchSize": ${fetchSize}
    }
}', '通用关系型数据库读取模板'),
        (5001, 'wH', '{
    "name": "hdfswriter",
    "parameter": {
      "defaultFS": "hdfs://yytz",
      "fileType": "${fileType}",
      "compress": "${compress}",
      "path": "${path}",
      "fileName": "addax",
      "writeMode": "overwrite",
      "column": ${column},
      "createPath": true,
      "haveKerberos": false,
      "hdfsSitePath": "/etc/hadoop/conf/hdfs-site.xml",
      "hadoopConfig": {}
    }
}', 'HDFS写入模板'),
        (5000, 'R2H', '{
  "job": {
    "content": {
        "reader": ${reader},
         "writer": ${writer},
      "setting": {
        "speed": {
          "batchSize": 20480,
          "bytes": -1,
          "channel": 1
        }
      }
    }
  }
}', '关系型数据库到HDFS的模板');


select insert_dates_for_year(extract('year' from now())::integer, 1021);

insert into public.etl_target (code, name, target_type, enabled, is_default, remark)
values ('DEFAULT_HIVE', '默认Hive目标端', 'HIVE', true, true, '系统初始化默认目标端')
on conflict (code) do nothing;

update public.etl_table t
set target_id = tt.id
from public.etl_target tt
where tt.code = 'DEFAULT_HIVE'
  and t.target_id is null;
