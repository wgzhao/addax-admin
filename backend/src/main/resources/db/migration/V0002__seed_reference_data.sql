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
(1021, '交易日表', 'FR', '自动从柜台取最新的交易日表信息'),
(1022, '工作日历', 'FR', '工作日(含调休)日期表，item_key 为 yyyyMMdd 的业务日期'),
(2011, 'ODS采集类型映射', 'FR', '源表的字段类型映射至HADOOP'),
(5000, 'JOB模板', 'AT', 'JSON的主模板，与5001关联'),
(5001, 'JOB模板子模块', 'AT', '实际读写数据库的JSON模块');


insert into public.sys_item (dict_code, item_key, item_value, remark)
values  (1000, 'CONCURRENT_LIMIT', '30', '最大采集并发数量'),
        (1000, 'ADDAX', '/opt/app/addax', 'Addax程序目录'),
        (1000, 'QUEUE_SIZE', '40', '采集队列长度'),
        (1000, 'SWITCH_TIME', '16:20', '切日时间'),
        (1000, 'SCHEMA_REFRESH_TIMEOUT', '600', '表结构刷新超时秒数');


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

-- Job templates (R2H/R2R/rR/wH/wR) are seeded at startup by DictService#ensureRdbmsTemplates
-- from the Java constants, which are the single source of truth for both the defaults and the
-- placeholder contract.

select insert_dates_for_year(extract('year' from now())::integer, 1021);

-- Targets are user-configured resources: HDFS is no longer seeded as the implicit default.
