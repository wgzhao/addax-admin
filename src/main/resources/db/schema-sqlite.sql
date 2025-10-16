-- SQLite schema migrated from PostgreSQL
-- This file contains the table structures for SQLite database

-- Enable foreign keys
PRAGMA foreign_keys = ON;

-- Table: addax_log
CREATE TABLE addax_log
(
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    tid      INTEGER NOT NULL,
    run_at   DATETIME,
    run_date DATE,
    log      TEXT,
    FOREIGN KEY (tid) REFERENCES etl_table (id)
);
CREATE INDEX idx_tid_run_date ON addax_log (tid, run_at);

-- Table: users
CREATE TABLE users
(
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  INTEGER      NOT NULL
);

-- Table: authorities
CREATE TABLE authorities
(
    username  VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES users (username),
    UNIQUE (username, authority)
);

-- Table: etl_source
CREATE TABLE etl_source
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    code         VARCHAR(10)  NOT NULL,
    name         VARCHAR(200) NOT NULL,
    url          VARCHAR(500) NOT NULL,
    username     VARCHAR(64),
    pass         VARCHAR(64),
    start_at     TIME,
    prerequisite VARCHAR(4000),
    pre_script   VARCHAR(4000),
    remark       VARCHAR(2000),
    enabled      INTEGER DEFAULT 1
);

-- Table: etl_table
CREATE TABLE etl_table
(
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    source_db       VARCHAR(32)                    NOT NULL,
    source_table    VARCHAR(64)                    NOT NULL,
    target_db       VARCHAR(50)                    NOT NULL,
    target_table    VARCHAR(200)                   NOT NULL,
    part_kind       CHAR(1)       DEFAULT 'D',
    part_name       VARCHAR(20)   DEFAULT 'logdate',
    filter          VARCHAR(2000) DEFAULT '1=1'    NOT NULL,
    kind            CHAR(1)       DEFAULT 'A',
    retry_cnt       INTEGER       DEFAULT 3,
    start_time      DATETIME      DEFAULT CURRENT_TIMESTAMP,
    end_time        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    max_runtime     INTEGER       DEFAULT 2000,
    sid             INTEGER                        NOT NULL,
    duration        INTEGER       DEFAULT 0        NOT NULL,
    part_format     VARCHAR(10)   DEFAULT 'yyyyMMdd',
    storage_format  VARCHAR(10)   DEFAULT 'orc'    NOT NULL,
    compress_format VARCHAR(10)   DEFAULT 'snappy' NOT NULL,
    tbl_comment     VARCHAR(500),
    status          CHAR(1)       DEFAULT 'U'      NOT NULL,
    FOREIGN KEY (sid) REFERENCES etl_source (id)
);

-- Table: etl_column
CREATE TABLE etl_column
(
    tid              INTEGER     NOT NULL,
    column_name      VARCHAR(255),
    column_id        INTEGER,
    source_type      VARCHAR(64),
    data_length      INTEGER,
    data_precision   INTEGER,
    data_scale       INTEGER,
    col_comment      VARCHAR(4000),
    target_type      VARCHAR(50) NOT NULL,
    target_type_full VARCHAR(100),
    update_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (tid) REFERENCES etl_table (id),
    UNIQUE (tid, column_name)
);

-- Table: etl_job
CREATE TABLE etl_job
(
    tid INTEGER NOT NULL,
    job TEXT    NOT NULL,
    FOREIGN KEY (tid) REFERENCES etl_table (id)
);

-- Table: etl_jour
CREATE TABLE etl_jour
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    tid       INTEGER,
    kind      VARCHAR(32),
    start_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    duration  INTEGER  DEFAULT 0                 NOT NULL,
    status    INTEGER  DEFAULT 1,
    cmd       TEXT,
    error_msg VARCHAR(4000),
    FOREIGN KEY (tid) REFERENCES etl_table (id)
);
CREATE INDEX idx_etl_jour_tid ON etl_jour (tid);

-- Table: etl_soutab
CREATE TABLE etl_soutab
(
    sou_db_conn    VARCHAR(64) NOT NULL,
    owner          VARCHAR(64) NOT NULL,
    table_name     VARCHAR(64) NOT NULL,
    column_name    VARCHAR(64) NOT NULL,
    data_type      VARCHAR(64),
    data_length    INTEGER,
    data_precision INTEGER,
    data_scale     INTEGER,
    column_id      INTEGER,
    table_type     VARCHAR(32),
    tab_comment    VARCHAR(2000),
    col_comment    VARCHAR(2000),
    dw_clt_date    DATETIME DEFAULT CURRENT_TIMESTAMP,
    tid            VARCHAR(32) NOT NULL
);

-- Table: etl_statistic
CREATE TABLE etl_statistic
(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    tid          INTEGER,
    start_at     DATETIME,
    end_at       DATETIME,
    take_secs    INTEGER,
    total_bytes  INTEGER,
    byte_speed   INTEGER,
    rec_speed    INTEGER,
    total_recs   INTEGER,
    total_errors INTEGER,
    run_date     DATE DEFAULT CURRENT_DATE NOT NULL
);
CREATE UNIQUE INDEX idx_sta_tid ON etl_statistic (tid, run_date);

-- Table: groups
CREATE TABLE groups
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    group_name VARCHAR(50) NOT NULL
);

-- Table: group_members
CREATE TABLE group_members
(
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    username VARCHAR(50) NOT NULL,
    group_id INTEGER     NOT NULL,
    FOREIGN KEY (group_id) REFERENCES groups (id)
);

-- Table: group_authorities
CREATE TABLE group_authorities
(
    group_id  INTEGER     NOT NULL,
    authority VARCHAR(50) NOT NULL,
    FOREIGN KEY (group_id) REFERENCES groups (id)
);

-- Table: notification
CREATE TABLE notification
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    phone     VARCHAR(255)                       NOT NULL,
    msg       VARCHAR(500)                       NOT NULL,
    sms       CHAR(1)  DEFAULT 'Y'               NOT NULL,
    im        CHAR(1)  DEFAULT 'Y'               NOT NULL,
    call      CHAR(1)  DEFAULT 'N'               NOT NULL,
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Table: sys_dict
CREATE TABLE sys_dict
(
    code           INTEGER PRIMARY KEY,
    name           VARCHAR(255),
    classification VARCHAR(2000),
    remark         VARCHAR(500)
);

-- Table: sys_item
CREATE TABLE sys_item
(
    dict_code  INTEGER      NOT NULL,
    item_key   VARCHAR(255) NOT NULL,
    item_value VARCHAR(2000),
    remark     VARCHAR(4000),
    FOREIGN KEY (dict_code) REFERENCES sys_dict (code) ON UPDATE CASCADE ON DELETE CASCADE
);

-- Table: vw_etl_table_with_source
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
    JOIN etl_source s ON t.sid = s.id;
