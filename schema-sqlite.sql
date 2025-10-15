-- SQLite schema migrated from PostgreSQL
-- This file contains the table structures for SQLite database

-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- Table: addax_log
CREATE TABLE addax_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tid INTEGER NOT NULL,
    run_at DATETIME,
    run_date DATE,
    log TEXT
);

-- Table: authorities
CREATE TABLE authorities (
    username VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL
);

-- Table: etl_column (采集的表字段信息，包括源表和目标表)
CREATE TABLE etl_column (
    -- tid: 采集表主键 ID，对应 tb_etl_table 中的 tid
    tid INTEGER NOT NULL,
    -- column_name: 原表字段名称
    column_name VARCHAR(255),
    -- column_id: 列 ID，用于排序字段
    column_id INTEGER,
    -- source_type: 源表的数据类型
    source_type VARCHAR(64),
    -- data_length: 数据长度
    data_length INTEGER,
    -- data_precision: 精度
    data_precision INTEGER,
    -- data_scale: 小数位
    data_scale INTEGER,
    -- col_comment: 字段注释
    col_comment VARCHAR(4000),
    -- target_type: 目标表对应的类型
    target_type VARCHAR(50) NOT NULL,
    -- target_type_full: 目标表字段的完整类型，比如 decimal(10,3)
    target_type_full VARCHAR(100),
    -- update_at: 更新时间
    update_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Table: etl_job (采集表的 addax 任务模板)
CREATE TABLE etl_job (
    -- tid: 采集表主键
    tid INTEGER NOT NULL,
    -- job: addax 任务模板
    job TEXT NOT NULL
);

-- Table: etl_jour
CREATE TABLE etl_jour (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tid INTEGER,
    kind VARCHAR(32),
    start_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    duration INTEGER DEFAULT 0 NOT NULL,
    status INTEGER DEFAULT 1,
    cmd TEXT,
    error_msg VARCHAR(4000)
);

-- Table: etl_source (采集源表)
CREATE TABLE etl_source (
    -- id: 采集源 ID
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- code: 采集编号,一般以两个大写字母作为标志
    code VARCHAR(10) NOT NULL,
    -- name: 采集源名称
    name VARCHAR(200) NOT NULL,
    -- url: 采集源 JDBC 连接串
    url VARCHAR(500) NOT NULL,
    -- username: 采集源连接的账号
    username VARCHAR(64),
    -- pass: 采集源连接的密码
    pass VARCHAR(64),
    -- start_at: 采集的定时启动时间点，一般只考虑到小时和分钟，秒钟默认为 0
    start_at TIME,
    -- prerequisite: 能否开始采集的先决条件，比如获取采集标志位，或者等待数据不再更新，一般是一段 SQL，然后通过返回值真假进行判断
    prerequisite VARCHAR(4000),
    -- pre_script: 标志符合条件后的前置脚本
    pre_script VARCHAR(4000),
    -- remark: 系统备注信息
    remark VARCHAR(2000),
    -- enabled: 是否有效
    enabled INTEGER DEFAULT 1
);

-- Table: etl_soutab
CREATE TABLE etl_soutab (
    sou_db_conn VARCHAR(64) NOT NULL,
    owner VARCHAR(64) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    column_name VARCHAR(64) NOT NULL,
    data_type VARCHAR(64),
    data_length INTEGER,
    data_precision INTEGER,
    data_scale INTEGER,
    column_id INTEGER,
    table_type VARCHAR(32),
    tab_comment VARCHAR(2000),
    col_comment VARCHAR(2000),
    dw_clt_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    tid VARCHAR(32) NOT NULL
);

-- Table: etl_statistic (采集统计表)
CREATE TABLE etl_statistic (
    -- id: 逻辑主键
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- tid: 采集表主键
    tid INTEGER,
    -- start_at: 采集开始时间
    start_at DATETIME,
    -- end_at: 采集结束时间
    end_at DATETIME,
    -- take_secs: 采集耗时
    take_secs INTEGER,
    -- total_bytes: 采集的总字节数
    total_bytes INTEGER,
    -- byte_speed: 采集速度  字节/秒
    byte_speed INTEGER,
    -- rec_speed: 采集速度 行/秒
    rec_speed INTEGER,
    -- total_recs: 采集的总行数
    total_recs INTEGER,
    -- total_errors: 采集时发生错误的行数
    total_errors INTEGER,
    -- run_date: 运行时的日期
    run_date DATE DEFAULT CURRENT_DATE NOT NULL
);

-- Table: etl_table (采集表信息)
CREATE TABLE etl_table (
    -- id: 表 ID
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- source_db: 采集源库名或 schema名称或 owner 名称
    source_db VARCHAR(32) NOT NULL,
    -- source_table: 采集源表名
    source_table VARCHAR(64) NOT NULL,
    -- target_db: 目标库名，即提供给 hive 的库名
    target_db VARCHAR(50) NOT NULL,
    -- target_table: 目标表名，即 Hive 的表名
    target_table VARCHAR(200) NOT NULL,
    -- part_kind: 分区类型，D - 按每日分区，如果为空，则表示不分区
    part_kind CHAR(1) DEFAULT 'D',
    -- part_name: 目标表分区字段名称，如果 dest_part_kind 不为空，则该字段也不能为空
    part_name VARCHAR(20) DEFAULT 'logdate',
    -- filter: 采集过滤条件，即 where 条件
    filter VARCHAR(2000) DEFAULT '1=1' NOT NULL,
    -- kind: 采集类型: A - 自动采集(默认值); M - 手工采集; R - 实时采集
    kind CHAR(1) DEFAULT 'A',
    -- retry_cnt: 采集的重试次数，用于采集失败时，可以多次尝试
    retry_cnt INTEGER DEFAULT 3,
    -- start_time: 本次采集的开始时间
    start_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    -- end_time: 本次采集的结束时间
    end_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    -- max_runtime: 采集可只持续的最大时间，避免某些采集因为网络或数据源原因一直无法结束
    max_runtime INTEGER DEFAULT 2000,
    -- sid: 采集源 ID，对应 etl_source 表 id
    sid INTEGER NOT NULL,
    -- duration: 采集耗时，单位为秒
    duration INTEGER DEFAULT 0 NOT NULL,
    -- part_format: 分区字段日期格式
    part_format VARCHAR(10) DEFAULT 'yyyyMMdd',
    -- storage_format: 压缩格式，可以是snappy,zlib,lz4,gz,bz2,zstd 等
    storage_format VARCHAR(10) DEFAULT 'orc' NOT NULL,
    -- compress_format: 压缩格式，可以是snappy,zlib,lz4,gz,bz2,zstd 等
    compress_format VARCHAR(10) DEFAULT 'snappy' NOT NULL,
    -- tbl_comment: 表注释
    tbl_comment VARCHAR(500),
    -- status: 状态
    status CHAR(1) DEFAULT 'U' NOT NULL
);

-- Table: group_authorities
CREATE TABLE group_authorities (
    group_id INTEGER NOT NULL,
    authority VARCHAR(50) NOT NULL
);

-- Table: group_members
CREATE TABLE group_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL,
    group_id INTEGER NOT NULL
);

-- Table: groups
CREATE TABLE groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_name VARCHAR(50) NOT NULL
);

-- Table: notification (数据中心消息提醒总表)
CREATE TABLE notification (
    -- id: 自动生成，无需理会
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- phone: 接收人号码或者其他唯一标识，用逗号分隔
    phone VARCHAR(255) NOT NULL,
    -- msg: 消息内容
    msg VARCHAR(500) NOT NULL,
    -- sms: 是否发送短信，发送成功后置为y
    sms CHAR(1) DEFAULT 'Y' NOT NULL,
    -- im: 是否发送企微，发送成功后置为y
    im CHAR(1) DEFAULT 'Y' NOT NULL,
    -- call: 是否拨打语音，拨打成功后置为y
    call CHAR(1) DEFAULT 'N' NOT NULL,
    -- create_at: 消息生成的时间，自动生成
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Table: sys_dict (字典条目表)
CREATE TABLE sys_dict (
    -- code: 条目编号
    code INTEGER PRIMARY KEY,
    -- name: 条目名称
    name VARCHAR(255),
    -- classification: 分类
    classification VARCHAR(2000),
    -- remark: 说明
    remark VARCHAR(500)
);

-- Table: sys_item (字典明细表)
CREATE TABLE sys_item (
    -- dict_code: 字典条目编号
    dict_code INTEGER NOT NULL,
    -- item_key: 明细名称
    item_key VARCHAR(255) NOT NULL,
    -- item_value: 明细内容
    item_value VARCHAR(2000),
    -- remark: 备注
    remark VARCHAR(4000)
);

-- Table: users
CREATE TABLE users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled INTEGER NOT NULL
);

-- View: vw_etl_table_with_source
CREATE VIEW vw_etl_table_with_source AS
SELECT t.id,
    t.source_db,
    t.source_table,
    t.target_db,
    t.target_table,
    t.part_kind,
    t.part_name,
    t.filter,
    t.kind,
    t.retry_cnt,
    t.start_time,
    t.end_time,
    t.max_runtime,
    t.sid,
    t.duration,
    t.part_format,
    t.storage_format,
    t.compress_format,
    t.tbl_comment,
    t.status,
    s.code,
    s.name,
    s.url,
    s.username,
    s.pass,
    s.start_at,
    s.enabled
FROM etl_table t
LEFT JOIN etl_source s ON t.sid = s.id;

-- Indexes
CREATE INDEX idx_etl_jour_tid ON etl_jour (tid);
CREATE UNIQUE INDEX idx_sta_tid ON etl_statistic (tid, run_date);
CREATE INDEX idx_tid_run_date ON addax_log (tid, run_at);
CREATE UNIQUE INDEX ix_auth_username ON authorities (username, authority);
CREATE UNIQUE INDEX uk_tid_column_name ON etl_column (tid, column_name);

-- Constraints (Primary Keys are already defined)
-- Foreign Keys
ALTER TABLE etl_column ADD CONSTRAINT etl_column_tid_fk FOREIGN KEY (tid) REFERENCES etl_table(id);
ALTER TABLE etl_job ADD CONSTRAINT etl_job_tid_fk FOREIGN KEY (tid) REFERENCES etl_table(id);
ALTER TABLE etl_jour ADD CONSTRAINT etl_jour_tid_fk FOREIGN KEY (tid) REFERENCES etl_table(id);
ALTER TABLE etl_table ADD CONSTRAINT etl_table_sid_fk FOREIGN KEY (sid) REFERENCES etl_source(id);
ALTER TABLE authorities ADD CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users(username);
ALTER TABLE group_authorities ADD CONSTRAINT fk_group_authorities_group FOREIGN KEY (group_id) REFERENCES groups(id);
ALTER TABLE group_members ADD CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES groups(id);
ALTER TABLE sys_item ADD CONSTRAINT tb_item_dict_fk FOREIGN KEY (dict_code) REFERENCES sys_dict(code) ON UPDATE CASCADE ON DELETE CASCADE;
