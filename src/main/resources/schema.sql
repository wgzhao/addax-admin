create table public.sys_dict
(
    code              smallint not null
        constraint pk_tb_dict
            primary key,
    name              varchar(255),
    "classification " varchar(2000),
    remark            varchar(500)
);

comment on table public.sys_dict is '字典条目表';

comment on column public.sys_dict.code is '条目编号';

comment on column public.sys_dict.name is '条目名称';

comment on column public.sys_dict."classification " is '分类';

comment on column public.sys_dict.remark is '说明';

alter table public.sys_dict
    owner to stg01;

create table public.sys_item
(
    dict_code    smallint     not null
        constraint tb_item_dict_fk
            references public.sys_dict
        on update cascade on delete cascade,
    item_value   varchar(255) not null,
    item_content varchar(2000),
    remark       varchar(4000),
    constraint pk_tb_dictionary
        primary key (dict_code, item_value)
);

comment on table public.sys_item is '字典明细表';

comment on column public.sys_item.dict_code is '字典条目编号';

comment on column public.sys_item.item_value is '明细名称';

comment on column public.sys_item.item_content is '明细内容';

comment on column public.sys_item.remark is '备注';

alter table public.sys_item
    owner to stg01;

create table public.notification
(
    mid         varchar(36) default gen_random_uuid() not null,
    phone       varchar(255)                          not null,
    msg         varchar(500)                          not null,
    dw_clt_date timestamp   default CURRENT_TIMESTAMP not null,
    bsms        char        default 'Y'::bpchar       not null,
    bkk         char        default 'Y'::bpchar       not null,
    bcall       char        default 'N'::bpchar       not null
);

comment on table public.notification is '数据中心消息提醒总表';

comment on column public.notification.mid is '自动生成，无需理会';

comment on column public.notification.phone is '接收人号码或者其他唯一标识，用逗号分隔';

comment on column public.notification.msg is '消息内容';

comment on column public.notification.dw_clt_date is '消息生成的时间，自动生成';

comment on column public.notification.bsms is '是否发送短信，发送成功后置为y';

comment on column public.notification.bkk is '是否发送KK，发送成功后置为y';

comment on column public.notification.bcall is '是否拨打语音，拨打成功后置为y';

alter table public.notification
    owner to stg01;

create table public.etl_source
(
    name         varchar(200) not null,
    url          varchar(500) not null,
    code         varchar(2)   not null,
    username     varchar(64),
    pass         varchar(64),
    start_at     time,
    prerequisite varchar(4000),
    pre_script   varchar(4000),
    remark       varchar(2000),
    did          varchar(36) default gen_random_uuid(),
    enabled      char        default 'Y'::bpchar
);

comment on table public.etl_source is '采集源表';

comment on column public.etl_source.name is '采集源名称';

comment on column public.etl_source.url is '采集源 JDBC 连接串';

comment on column public.etl_source.code is '采集编号,一般以两个大写字母作为标志';

comment on column public.etl_source.username is '采集源连接的账号';

comment on column public.etl_source.pass is '采集源连接的密码';

comment on column public.etl_source.start_at is '采集的定时启动时间点，一般只考虑到小时和分钟，秒钟默认为 0';

comment on column public.etl_source.prerequisite is '能否开始采集的先决条件，比如获取采集标志位，或者等待数据不再更新，一般是一段 SQL，然后通过返回值真假进行判断';

comment on column public.etl_source.pre_script is '标志符合条件后的前置脚本';

comment on column public.etl_source.remark is '系统备注信息';

comment on column public.etl_source.did is '采集源 ID';

comment on column public.etl_source.enabled is '是否有效';

alter table public.etl_source
    owner to stg01;

create table public.etl_tbls
(
    db_id        integer,
    db_name      varchar(255) not null,
    db_location  varchar(2000),
    tbl_id       integer,
    tbl_name     varchar(255) not null,
    tbl_type     varchar(255),
    tbl_location varchar(2000),
    cd_id        integer,
    col_name     varchar(255) not null,
    col_type     varchar(500),
    col_comment  varchar(2000),
    col_idx      integer,
    tbl_comment  varchar(2000),
    constraint pk_tb_imp_etl_tbls
        primary key (db_name, tbl_name, col_name)
);

comment on table public.etl_tbls is 'HIVE的表结构信息';

comment on column public.etl_tbls.db_name is '数据库名、用户';

comment on column public.etl_tbls.db_location is '数据库所在HDFS地址';

comment on column public.etl_tbls.tbl_name is '表名';

comment on column public.etl_tbls.tbl_type is '表类型（管理表、外部表）';

comment on column public.etl_tbls.tbl_location is '表所在HDFS地址';

comment on column public.etl_tbls.col_name is '字段名';

comment on column public.etl_tbls.col_type is '字段类型';

comment on column public.etl_tbls.col_comment is '字段备注';

comment on column public.etl_tbls.col_idx is '字段顺序';

comment on column public.etl_tbls.tbl_comment is '表注释';

alter table public.etl_tbls
    owner to stg01;

create table public.tb_imp_etl_tbls_tmp
(
    db_id        numeric(19),
    db_name      varchar(255),
    db_location  varchar(255),
    tbl_id       numeric(19),
    tbl_name     varchar(255),
    tbl_type     varchar(255),
    tbl_location varchar(255),
    cd_id        numeric(19),
    col_name     varchar(255),
    col_type     varchar(500),
    col_comment  varchar(2000),
    col_idx      numeric(19),
    tbl_comment  varchar(2000)
);

alter table public.tb_imp_etl_tbls_tmp
    owner to stg01;

create table public.tb_imp_flag
(
    tradedate   numeric(10),
    kind        varchar(32)                         not null,
    fid         varchar(32)                         not null,
    fval        integer,
    dw_clt_date timestamp default CURRENT_TIMESTAMP not null
);

alter table public.tb_imp_flag
    owner to stg01;

create table public.tb_imp_jour
(
    kind       varchar(32),
    trade_date numeric(10),
    status     varchar(32),
    key_id     varchar(4000),
    remark     varchar(4000),
    updt_date  timestamp default CURRENT_TIMESTAMP
);

alter table public.tb_imp_jour
    owner to stg01;

create table public.tb_imp_param0
(
    param_sou    char,
    param_kind_0 varchar(255),
    param_kind   varchar(258),
    param_name   varchar(2000),
    param_remark varchar(4000),
    param_value  varchar(4000)
);

comment on table public.tb_imp_param0 is '日期参数文件基表';

alter table public.tb_imp_param0
    owner to stg01;

create table public.tb_imp_plan
(
    pn_type     char,
    pn_fixed    varchar(200),
    pn_interval integer,
    pn_range    varchar(32),
    pn_id       varchar(36) default gen_random_uuid() not null
        constraint pk_imp_plan
            primary key,
    flag        char        default 'N'::bpchar,
    start_time  timestamp   default CURRENT_TIMESTAMP,
    end_time    timestamp   default CURRENT_TIMESTAMP,
    bexit       char        default 'Y'::bpchar,
    runtime     numeric(10)
);

comment on column public.tb_imp_plan.pn_type is '定时计划类型，参考参数1064';

comment on column public.tb_imp_plan.pn_fixed is '定点时间';

comment on column public.tb_imp_plan.pn_interval is '轮询间隔';

comment on column public.tb_imp_plan.pn_range is '轮询时间区间';

comment on column public.tb_imp_plan.bexit is '是否跳过切日时间段';

alter table public.tb_imp_plan
    owner to stg01;

create table public.tb_imp_tbl_hdp
(
    tid            varchar(32) not null,
    hive_owner     varchar(35),
    hive_tablename varchar(64) not null,
    col_name       varchar(255),
    col_type_full  varchar(500),
    col_type       varchar(2000),
    col_precision  integer,
    col_scale      integer,
    col_idx        integer,
    tbl_comment    varchar(4000),
    col_comment    varchar(4000),
    updt_date      timestamp default CURRENT_TIMESTAMP,
    cd_id          integer
);

alter table public.tb_imp_tbl_hdp
    owner to stg01;

create index idx_tb_imp_tbl_hdp
    on public.tb_imp_tbl_hdp (tid);

create unique index uk_tb_imp_tbl_hdp
    on public.tb_imp_tbl_hdp (tid, col_name);

create table public.etl_column
(
    tid              bigint      not null,
    column_name      varchar(255),
    column_id        numeric(10),
    source_type      varchar(64),
    data_length      numeric(10),
    data_precision   integer,
    data_scale       integer,
    tbl_comment      varchar(4000),
    col_comment      varchar(4000),
    target_type      varchar(50) not null,
    target_type_full varchar(100),
    update_at        timestamp default CURRENT_TIMESTAMP
);

comment on table public.etl_column is '采集的表字段信息，包括源表和目标表';

comment on column public.etl_column.tid is '采集表主键 ID，对应 tb_etl_table 中的 tid';

comment on column public.etl_column.column_name is '原表字段名称';

comment on column public.etl_column.column_id is '列 ID，用于排序字段';

comment on column public.etl_column.source_type is '源表的数据类型';

comment on column public.etl_column.data_length is '数据长度';

comment on column public.etl_column.data_precision is '精度';

comment on column public.etl_column.data_scale is '小数位';

comment on column public.etl_column.tbl_comment is '表注释';

comment on column public.etl_column.col_comment is '字段注释';

comment on column public.etl_column.target_type is '目标表对应的类型';

comment on column public.etl_column.target_type_full is '目标表字段的完整类型，比如 decimal(10,3)';

comment on column public.etl_column.update_at is '更新时间';

alter table public.etl_column
    owner to stg01;

create index idx_tb_imp_tbl_sou
    on public.etl_column (tid);

create unique index uk_tb_imp_tbl_sou
    on public.etl_column (tid, column_name);

create table public.users
(
    username varchar(50)  not null
        primary key,
    password varchar(500) not null,
    enabled  boolean      not null
);

alter table public.users
    owner to stg01;

create table public.authorities
(
    username  varchar(50) not null
        constraint fk_authorities_users
            references public.users,
    authority varchar(50) not null
);

alter table public.authorities
    owner to stg01;

create unique index ix_auth_username
    on public.authorities (username, authority);

create table public.groups
(
    id         bigint generated by default as identity
        primary key,
    group_name varchar(50) not null
);

alter table public.groups
    owner to stg01;

create table public.group_authorities
(
    group_id  bigint      not null
        constraint fk_group_authorities_group
            references public.groups,
    authority varchar(50) not null
);

alter table public.group_authorities
    owner to stg01;

create table public.group_members
(
    id       bigint generated by default as identity
        primary key,
    username varchar(50) not null,
    group_id bigint      not null
        constraint fk_group_members_group
            references public.groups
);

alter table public.group_members
    owner to stg01;

create table public.etl_job
(
    tid varchar(32) not null
        constraint pk_tb_job
            primary key,
    job text        not null
);

comment on table public.etl_job is '采集表的 addax 任务模板';

comment on column public.etl_job.tid is '采集表主键';

comment on column public.etl_job.job is 'addax 任务模板';

alter table public.etl_job
    owner to stg01;

create table public.etl_soutab
(
    sou_db_conn    varchar(64) not null,
    owner          varchar(64) not null,
    table_name     varchar(64) not null,
    column_name    varchar(64) not null,
    data_type      varchar(64),
    data_length    bigint,
    data_precision integer,
    data_scale     integer,
    column_id      integer,
    table_type     varchar(32),
    tab_comment    varchar(2000),
    col_comment    varchar(2000),
    dw_clt_date    timestamp default CURRENT_TIMESTAMP,
    tid            varchar(32) not null,
    constraint pk_tid_colname_idx
        primary key (tid, column_name)
);

alter table public.etl_soutab
    owner to stg01;

create table public.addax_log
(
    id       bigserial
        primary key,
    tid      char(32),
    run_at   timestamp,
    run_date date,
    log      text
);

alter table public.addax_log
    owner to stg01;

create index idx_tid_run_date
    on public.addax_log (tid, run_at);

create table public.etl_statistic
(
    id           bigint                    not null
        constraint tb_addax_statistic_pkey
            primary key,
    tid          bigint,
    start_at     timestamp,
    end_at       timestamp,
    take_secs    integer,
    total_bytes  integer,
    byte_speed   integer,
    rec_speed    integer,
    total_recs   bigint,
    total_errors integer,
    run_date     date default CURRENT_DATE not null
);

comment on table public.etl_statistic is '采集统计表';

comment on column public.etl_statistic.id is '逻辑主键';

comment on column public.etl_statistic.tid is '采集表主键';

comment on column public.etl_statistic.start_at is '采集开始时间';

comment on column public.etl_statistic.end_at is '采集结束时间';

comment on column public.etl_statistic.take_secs is '采集耗时';

comment on column public.etl_statistic.total_bytes is '采集的总字节数';

comment on column public.etl_statistic.byte_speed is '采集速度  字节/秒';

comment on column public.etl_statistic.rec_speed is '采集速度 行/秒';

comment on column public.etl_statistic.total_recs is '采集的总行数';

comment on column public.etl_statistic.total_errors is '采集时发生错误的行数';

comment on column public.etl_statistic.run_date is '运行时的日期';

alter table public.etl_statistic
    owner to stg01;

create unique index idx_sta_tid
    on public.etl_statistic (tid, run_date);

create table public.etl_table
(
    tid          bigint        default nextval('tb_imp_etl_tid_seq'::regclass) not null
        constraint tb_imp_etl_pkey1
            primary key,
    source_db    varchar(32)                                                   not null,
    source_table varchar(64)                                                   not null,
    target_db    varchar(50)                                                   not null,
    target_table varchar(200)                                                  not null,
    part_kind    char          default 'D'::bpchar,
    part_name    varchar(20)   default 'logdate'::character varying,
    filter       varchar(2000) default '1=1'::character varying                not null,
    status       char          default 'W'::bpchar,
    kind         char          default 'A'::bpchar,
    update_flag  char          default 'Y'::bpchar,
    create_flag  char          default 'Y'::bpchar,
    retry_cnt    integer       default 3,
    start_time   timestamp     default CURRENT_TIMESTAMP,
    end_time     timestamp     default CURRENT_TIMESTAMP,
    max_runtime  integer       default 2000,
    did          integer,
    duration     integer       default 0                                       not null
);

comment on table public.etl_table is '采集表信息';

comment on column public.etl_table.tid is '表 ID';

comment on column public.etl_table.source_db is '采集源库名或 schema名称或 owner 名称';

comment on column public.etl_table.source_table is '采集源表名';

comment on column public.etl_table.target_db is '目标库名，即提供给 hive 的库名';

comment on column public.etl_table.target_table is '目标表名，即 Hive 的表名';

comment on column public.etl_table.part_kind is '分区类型，D - 按每日分区，如果为空，则表示不分区';

comment on column public.etl_table.part_name is '目标表分区字段名称，如果 dest_part_kind 不为空，则该字段也不能为空';

comment on column public.etl_table.filter is '采集过滤条件，即 where 条件';

comment on column public.etl_table.status is '当前采集状态： N - 还没有采集; W - 等待采集; E - 采集发生错误; R - 正在采集; X - 不再采集';

comment on column public.etl_table.kind is '采集类型: A - 自动采集(默认值); M - 手工采集; R - 实时采集';

comment on column public.etl_table.update_flag is '表的元数据信息更新标志，主要是字段的变更: Y -  需要更新;  N - 不需要更新; R - 正在更新';

comment on column public.etl_table.create_flag is '目标表创建标志, Y - 已经创建, N -  需要创建或更新; R - 正在创建';

comment on column public.etl_table.retry_cnt is '采集的重试次数，用于采集失败时，可以多次尝试';

comment on column public.etl_table.start_time is '本次采集的开始时间';

comment on column public.etl_table.end_time is '本次采集的结束时间';

comment on column public.etl_table.max_runtime is '采集可只持续的最大时间，避免某些采集因为网络或数据源原因一直无法结束';

comment on column public.etl_table.did is '采集源 ID，对应 tb_imp_db 表 did';

comment on column public.etl_table.duration is '采集耗时，单位为秒';

alter table public.etl_table
    owner to stg01;

