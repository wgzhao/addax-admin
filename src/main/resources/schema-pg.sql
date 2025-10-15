--
-- PostgreSQL database dump
--

\restrict 2DW06DgDie3lCSkHqUrWZPxAad8bl9T3eRfuignMYDBOqT8GE0wSCNrzqonZy4R

-- Dumped from database version 16.10 (Debian 16.10-1.pgdg13+1)
-- Dumped by pg_dump version 16.10 (Debian 16.10-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: addax_log; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.addax_log (
    id bigint NOT NULL,
    tid bigint NOT NULL,
    run_at timestamp without time zone,
    run_date date,
    log text
);


ALTER TABLE public.addax_log OWNER TO addax_cdh;

--
-- Name: addax_log_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

CREATE SEQUENCE public.addax_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.addax_log_id_seq OWNER TO addax_cdh;

--
-- Name: addax_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: addax_cdh
--

ALTER SEQUENCE public.addax_log_id_seq OWNED BY public.addax_log.id;


--
-- Name: authorities; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.authorities (
    username character varying(50) NOT NULL,
    authority character varying(50) NOT NULL
);


ALTER TABLE public.authorities OWNER TO addax_cdh;

--
-- Name: etl_column; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_column (
    tid bigint NOT NULL,
    column_name character varying(255),
    column_id integer,
    source_type character varying(64),
    data_length integer,
    data_precision integer,
    data_scale integer,
    col_comment character varying(4000),
    target_type character varying(50) NOT NULL,
    target_type_full character varying(100),
    update_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.etl_column OWNER TO addax_cdh;

--
-- Name: TABLE etl_column; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.etl_column IS '采集的表字段信息，包括源表和目标表';


--
-- Name: COLUMN etl_column.tid; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.tid IS '采集表主键 ID，对应 tb_etl_table 中的 tid';


--
-- Name: COLUMN etl_column.column_name; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.column_name IS '原表字段名称';


--
-- Name: COLUMN etl_column.column_id; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.column_id IS '列 ID，用于排序字段';


--
-- Name: COLUMN etl_column.source_type; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.source_type IS '源表的数据类型';


--
-- Name: COLUMN etl_column.data_length; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.data_length IS '数据长度';


--
-- Name: COLUMN etl_column.data_precision; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.data_precision IS '精度';


--
-- Name: COLUMN etl_column.data_scale; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.data_scale IS '小数位';


--
-- Name: COLUMN etl_column.col_comment; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.col_comment IS '字段注释';


--
-- Name: COLUMN etl_column.target_type; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.target_type IS '目标表对应的类型';


--
-- Name: COLUMN etl_column.target_type_full; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.target_type_full IS '目标表字段的完整类型，比如 decimal(10,3)';


--
-- Name: COLUMN etl_column.update_at; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_column.update_at IS '更新时间';


--
-- Name: etl_job; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_job (
    tid bigint NOT NULL,
    job text NOT NULL
);


ALTER TABLE public.etl_job OWNER TO addax_cdh;

--
-- Name: TABLE etl_job; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.etl_job IS '采集表的 addax 任务模板';


--
-- Name: COLUMN etl_job.tid; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_job.tid IS '采集表主键';


--
-- Name: COLUMN etl_job.job; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_job.job IS 'addax 任务模板';


--
-- Name: etl_jour; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_jour (
    id bigint NOT NULL,
    tid bigint,
    kind character varying(32),
    start_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    duration integer DEFAULT 0 NOT NULL,
    status boolean DEFAULT true,
    cmd text,
    error_msg character varying(4000)
);


ALTER TABLE public.etl_jour OWNER TO addax_cdh;

--
-- Name: etl_jour_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

CREATE SEQUENCE public.etl_jour_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.etl_jour_id_seq OWNER TO addax_cdh;

--
-- Name: etl_jour_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: addax_cdh
--

ALTER SEQUENCE public.etl_jour_id_seq OWNED BY public.etl_jour.id;


--
-- Name: etl_source; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_source (
    id integer NOT NULL,
    code character varying(10) NOT NULL,
    name character varying(200) NOT NULL,
    url character varying(500) NOT NULL,
    username character varying(64),
    pass character varying(64),
    start_at time without time zone,
    prerequisite character varying(4000),
    pre_script character varying(4000),
    remark character varying(2000),
    enabled boolean DEFAULT true
);


ALTER TABLE public.etl_source OWNER TO addax_cdh;

--
-- Name: TABLE etl_source; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.etl_source IS '采集源表';


--
-- Name: COLUMN etl_source.id; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.id IS '采集源 ID';


--
-- Name: COLUMN etl_source.code; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.code IS '采集编号,一般以两个大写字母作为标志';


--
-- Name: COLUMN etl_source.name; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.name IS '采集源名称';


--
-- Name: COLUMN etl_source.url; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.url IS '采集源 JDBC 连接串';


--
-- Name: COLUMN etl_source.username; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.username IS '采集源连接的账号';


--
-- Name: COLUMN etl_source.pass; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.pass IS '采集源连接的密码';


--
-- Name: COLUMN etl_source.start_at; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.start_at IS '采集的定时启动时间点，一般只考虑到小时和分钟，秒钟默认为 0';


--
-- Name: COLUMN etl_source.prerequisite; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.prerequisite IS '能否开始采集的先决条件，比如获取采集标志位，或者等待数据不再更新，一般是一段 SQL，然后通过返回值真假进行判断';


--
-- Name: COLUMN etl_source.pre_script; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.pre_script IS '标志符合条件后的前置脚本';


--
-- Name: COLUMN etl_source.remark; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.remark IS '系统备注信息';


--
-- Name: COLUMN etl_source.enabled; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_source.enabled IS '是否有效';


--
-- Name: etl_source_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

CREATE SEQUENCE public.etl_source_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.etl_source_id_seq OWNER TO addax_cdh;

--
-- Name: etl_source_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: addax_cdh
--

ALTER SEQUENCE public.etl_source_id_seq OWNED BY public.etl_source.id;


--
-- Name: etl_soutab; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_soutab (
    sou_db_conn character varying(64) NOT NULL,
    owner character varying(64) NOT NULL,
    table_name character varying(64) NOT NULL,
    column_name character varying(64) NOT NULL,
    data_type character varying(64),
    data_length bigint,
    data_precision integer,
    data_scale integer,
    column_id integer,
    table_type character varying(32),
    tab_comment character varying(2000),
    col_comment character varying(2000),
    dw_clt_date timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    tid character varying(32) NOT NULL
);


ALTER TABLE public.etl_soutab OWNER TO addax_cdh;

--
-- Name: etl_statistic; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_statistic (
    id bigint NOT NULL,
    tid bigint,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    take_secs integer,
    total_bytes integer,
    byte_speed integer,
    rec_speed integer,
    total_recs bigint,
    total_errors integer,
    run_date date DEFAULT CURRENT_DATE NOT NULL
);


ALTER TABLE public.etl_statistic OWNER TO addax_cdh;

--
-- Name: TABLE etl_statistic; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.etl_statistic IS '采集统计表';


--
-- Name: COLUMN etl_statistic.id; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.id IS '逻辑主键';


--
-- Name: COLUMN etl_statistic.tid; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.tid IS '采集表主键';


--
-- Name: COLUMN etl_statistic.start_at; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.start_at IS '采集开始时间';


--
-- Name: COLUMN etl_statistic.end_at; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.end_at IS '采集结束时间';


--
-- Name: COLUMN etl_statistic.take_secs; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.take_secs IS '采集耗时';


--
-- Name: COLUMN etl_statistic.total_bytes; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.total_bytes IS '采集的总字节数';


--
-- Name: COLUMN etl_statistic.byte_speed; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.byte_speed IS '采集速度  字节/秒';


--
-- Name: COLUMN etl_statistic.rec_speed; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.rec_speed IS '采集速度 行/秒';


--
-- Name: COLUMN etl_statistic.total_recs; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.total_recs IS '采集的总行数';


--
-- Name: COLUMN etl_statistic.total_errors; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.total_errors IS '采集时发生错误的行数';


--
-- Name: COLUMN etl_statistic.run_date; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_statistic.run_date IS '运行时的日期';


--
-- Name: etl_statistic_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

ALTER TABLE public.etl_statistic ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.etl_statistic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: etl_table; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.etl_table (
    id bigint NOT NULL,
    source_db character varying(32) NOT NULL,
    source_table character varying(64) NOT NULL,
    target_db character varying(50) NOT NULL,
    target_table character varying(200) NOT NULL,
    part_kind character(1) DEFAULT 'D'::bpchar,
    part_name character varying(20) DEFAULT 'logdate'::character varying,
    filter character varying(2000) DEFAULT '1=1'::character varying NOT NULL,
    kind character(1) DEFAULT 'A'::bpchar,
    retry_cnt integer DEFAULT 3,
    start_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    end_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    max_runtime integer DEFAULT 2000,
    sid integer NOT NULL,
    duration integer DEFAULT 0 NOT NULL,
    part_format character varying(10) DEFAULT 'yyyyMMdd'::character varying,
    storage_format character varying(10) DEFAULT 'orc'::character varying NOT NULL,
    compress_format character varying(10) DEFAULT 'snappy'::character varying NOT NULL,
    tbl_comment character varying(500),
    status character(1) DEFAULT 'U'::bpchar NOT NULL
);


ALTER TABLE public.etl_table OWNER TO addax_cdh;

--
-- Name: TABLE etl_table; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.etl_table IS '采集表信息';


--
-- Name: COLUMN etl_table.id; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.id IS '表 ID';


--
-- Name: COLUMN etl_table.source_db; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.source_db IS '采集源库名或 schema名称或 owner 名称';


--
-- Name: COLUMN etl_table.source_table; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.source_table IS '采集源表名';


--
-- Name: COLUMN etl_table.target_db; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.target_db IS '目标库名，即提供给 hive 的库名';


--
-- Name: COLUMN etl_table.target_table; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.target_table IS '目标表名，即 Hive 的表名';


--
-- Name: COLUMN etl_table.part_kind; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.part_kind IS '分区类型，D - 按每日分区，如果为空，则表示不分区';


--
-- Name: COLUMN etl_table.part_name; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.part_name IS '目标表分区字段名称，如果 dest_part_kind 不为空，则该字段也不能为空';


--
-- Name: COLUMN etl_table.filter; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.filter IS '采集过滤条件，即 where 条件';


--
-- Name: COLUMN etl_table.kind; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.kind IS '采集类型: A - 自动采集(默认值); M - 手工采集; R - 实时采集';


--
-- Name: COLUMN etl_table.retry_cnt; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.retry_cnt IS '采集的重试次数，用于采集失败时，可以多次尝试';


--
-- Name: COLUMN etl_table.start_time; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.start_time IS '本次采集的开始时间';


--
-- Name: COLUMN etl_table.end_time; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.end_time IS '本次采集的结束时间';


--
-- Name: COLUMN etl_table.max_runtime; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.max_runtime IS '采集可只持续的最大时间，避免某些采集因为网络或数据源原因一直无法结束';


--
-- Name: COLUMN etl_table.sid; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.sid IS '采集源 ID，对应 etl_source 表 id';


--
-- Name: COLUMN etl_table.duration; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.duration IS '采集耗时，单位为秒';


--
-- Name: COLUMN etl_table.part_format; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.part_format IS '分区字段日期格式';


--
-- Name: COLUMN etl_table.storage_format; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.storage_format IS '压缩格式，可以是snappy,zlib,lz4,gz,bz2,zstd 等';


--
-- Name: COLUMN etl_table.tbl_comment; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.etl_table.tbl_comment IS '表注释';


--
-- Name: group_authorities; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.group_authorities (
    group_id bigint NOT NULL,
    authority character varying(50) NOT NULL
);


ALTER TABLE public.group_authorities OWNER TO addax_cdh;

--
-- Name: group_members; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.group_members (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    group_id bigint NOT NULL
);


ALTER TABLE public.group_members OWNER TO addax_cdh;

--
-- Name: group_members_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

ALTER TABLE public.group_members ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.group_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: groups; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.groups (
    id bigint NOT NULL,
    group_name character varying(50) NOT NULL
);


ALTER TABLE public.groups OWNER TO addax_cdh;

--
-- Name: groups_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

ALTER TABLE public.groups ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: notification; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.notification (
    id bigint NOT NULL,
    phone character varying(255) NOT NULL,
    msg character varying(500) NOT NULL,
    sms character(1) DEFAULT 'Y'::bpchar NOT NULL,
    im character(1) DEFAULT 'Y'::bpchar NOT NULL,
    call character(1) DEFAULT 'N'::bpchar NOT NULL,
    create_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public.notification OWNER TO addax_cdh;

--
-- Name: TABLE notification; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.notification IS '数据中心消息提醒总表';


--
-- Name: COLUMN notification.id; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.id IS '自动生成，无需理会';


--
-- Name: COLUMN notification.phone; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.phone IS '接收人号码或者其他唯一标识，用逗号分隔';


--
-- Name: COLUMN notification.msg; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.msg IS '消息内容';


--
-- Name: COLUMN notification.sms; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.sms IS '是否发送短信，发送成功后置为y';


--
-- Name: COLUMN notification.im; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.im IS '是否发送企微，发送成功后置为y';


--
-- Name: COLUMN notification.call; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.call IS '是否拨打语音，拨打成功后置为y';


--
-- Name: COLUMN notification.create_at; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.notification.create_at IS '消息生成的时间，自动生成';


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

CREATE SEQUENCE public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.notification_id_seq OWNER TO addax_cdh;

--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: addax_cdh
--

ALTER SEQUENCE public.notification_id_seq OWNED BY public.notification.id;


--
-- Name: sys_dict; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.sys_dict (
    code integer NOT NULL,
    name character varying(255),
    classification character varying(2000),
    remark character varying(500)
);


ALTER TABLE public.sys_dict OWNER TO addax_cdh;

--
-- Name: TABLE sys_dict; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.sys_dict IS '字典条目表';


--
-- Name: COLUMN sys_dict.code; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_dict.code IS '条目编号';


--
-- Name: COLUMN sys_dict.name; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_dict.name IS '条目名称';


--
-- Name: COLUMN sys_dict.classification; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_dict.classification IS '分类';


--
-- Name: COLUMN sys_dict.remark; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_dict.remark IS '说明';


--
-- Name: sys_item; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.sys_item (
    dict_code integer NOT NULL,
    item_key character varying(255) NOT NULL,
    item_value character varying(2000),
    remark character varying(4000)
);


ALTER TABLE public.sys_item OWNER TO addax_cdh;

--
-- Name: TABLE sys_item; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON TABLE public.sys_item IS '字典明细表';


--
-- Name: COLUMN sys_item.dict_code; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_item.dict_code IS '字典条目编号';


--
-- Name: COLUMN sys_item.item_key; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_item.item_key IS '明细名称';


--
-- Name: COLUMN sys_item.item_value; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_item.item_value IS '明细内容';


--
-- Name: COLUMN sys_item.remark; Type: COMMENT; Schema: public; Owner: addax_cdh
--

COMMENT ON COLUMN public.sys_item.remark IS '备注';


--
-- Name: tb_addax_statistic_id_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

CREATE SEQUENCE public.tb_addax_statistic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tb_addax_statistic_id_seq OWNER TO addax_cdh;

--
-- Name: tb_addax_statistic_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: addax_cdh
--

ALTER SEQUENCE public.tb_addax_statistic_id_seq OWNED BY public.etl_statistic.id;


--
-- Name: tb_imp_etl_tid_seq; Type: SEQUENCE; Schema: public; Owner: addax_cdh
--

CREATE SEQUENCE public.tb_imp_etl_tid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.tb_imp_etl_tid_seq OWNER TO addax_cdh;

--
-- Name: tb_imp_etl_tid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: addax_cdh
--

ALTER SEQUENCE public.tb_imp_etl_tid_seq OWNED BY public.etl_table.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: addax_cdh
--

CREATE TABLE public.users (
    username character varying(50) NOT NULL,
    password character varying(500) NOT NULL,
    enabled boolean NOT NULL
);


ALTER TABLE public.users OWNER TO addax_cdh;

--
-- Name: vw_etl_table_with_source; Type: VIEW; Schema: public; Owner: addax_cdh
--

CREATE VIEW public.vw_etl_table_with_source AS
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
   FROM (public.etl_table t
     LEFT JOIN public.etl_source s ON ((t.sid = s.id)));


ALTER VIEW public.vw_etl_table_with_source OWNER TO addax_cdh;

--
-- Name: addax_log id; Type: DEFAULT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.addax_log ALTER COLUMN id SET DEFAULT nextval('public.addax_log_id_seq'::regclass);


--
-- Name: etl_jour id; Type: DEFAULT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_jour ALTER COLUMN id SET DEFAULT nextval('public.etl_jour_id_seq'::regclass);


--
-- Name: etl_source id; Type: DEFAULT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_source ALTER COLUMN id SET DEFAULT nextval('public.etl_source_id_seq'::regclass);


--
-- Name: etl_table id; Type: DEFAULT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_table ALTER COLUMN id SET DEFAULT nextval('public.tb_imp_etl_tid_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_id_seq'::regclass);


--
-- Name: addax_log addax_log_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.addax_log
    ADD CONSTRAINT addax_log_pkey PRIMARY KEY (id);


--
-- Name: etl_jour etl_jour_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_jour
    ADD CONSTRAINT etl_jour_pkey PRIMARY KEY (id);


--
-- Name: etl_source etl_source_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_source
    ADD CONSTRAINT etl_source_pkey PRIMARY KEY (id);


--
-- Name: group_members group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT group_members_pkey PRIMARY KEY (id);


--
-- Name: groups groups_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: sys_dict pk_tb_dict; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.sys_dict
    ADD CONSTRAINT pk_tb_dict PRIMARY KEY (code);


--
-- Name: sys_item pk_tb_dictionary; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.sys_item
    ADD CONSTRAINT pk_tb_dictionary PRIMARY KEY (dict_code, item_key);


--
-- Name: etl_job pk_tb_job; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_job
    ADD CONSTRAINT pk_tb_job PRIMARY KEY (tid);


--
-- Name: etl_soutab pk_tid_colname_idx; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_soutab
    ADD CONSTRAINT pk_tid_colname_idx PRIMARY KEY (tid, column_name);


--
-- Name: etl_statistic tb_addax_statistic_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_statistic
    ADD CONSTRAINT tb_addax_statistic_pkey PRIMARY KEY (id);


--
-- Name: etl_table tb_imp_etl_pkey1; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_table
    ADD CONSTRAINT tb_imp_etl_pkey1 PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (username);


--
-- Name: idx_etl_jour_tid; Type: INDEX; Schema: public; Owner: addax_cdh
--

CREATE INDEX idx_etl_jour_tid ON public.etl_jour USING btree (tid);


--
-- Name: idx_sta_tid; Type: INDEX; Schema: public; Owner: addax_cdh
--

CREATE UNIQUE INDEX idx_sta_tid ON public.etl_statistic USING btree (tid, run_date);


--
-- Name: idx_tid_run_date; Type: INDEX; Schema: public; Owner: addax_cdh
--

CREATE INDEX idx_tid_run_date ON public.addax_log USING btree (tid, run_at);


--
-- Name: ix_auth_username; Type: INDEX; Schema: public; Owner: addax_cdh
--

CREATE UNIQUE INDEX ix_auth_username ON public.authorities USING btree (username, authority);


--
-- Name: uk_tid_column_name; Type: INDEX; Schema: public; Owner: addax_cdh
--

CREATE UNIQUE INDEX uk_tid_column_name ON public.etl_column USING btree (tid, column_name);


--
-- Name: etl_column etl_column_tid_fk; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_column
    ADD CONSTRAINT etl_column_tid_fk FOREIGN KEY (tid) REFERENCES public.etl_table(id);


--
-- Name: etl_job etl_job_tid_fk; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_job
    ADD CONSTRAINT etl_job_tid_fk FOREIGN KEY (tid) REFERENCES public.etl_table(id);


--
-- Name: etl_jour etl_jour_tid_fk; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_jour
    ADD CONSTRAINT etl_jour_tid_fk FOREIGN KEY (tid) REFERENCES public.etl_table(id);


--
-- Name: etl_table etl_table_sid_fk; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.etl_table
    ADD CONSTRAINT etl_table_sid_fk FOREIGN KEY (sid) REFERENCES public.etl_source(id);


--
-- Name: authorities fk_authorities_users; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.authorities
    ADD CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES public.users(username);


--
-- Name: group_authorities fk_group_authorities_group; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.group_authorities
    ADD CONSTRAINT fk_group_authorities_group FOREIGN KEY (group_id) REFERENCES public.groups(id);


--
-- Name: group_members fk_group_members_group; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES public.groups(id);


--
-- Name: sys_item tb_item_dict_fk; Type: FK CONSTRAINT; Schema: public; Owner: addax_cdh
--

ALTER TABLE ONLY public.sys_item
    ADD CONSTRAINT tb_item_dict_fk FOREIGN KEY (dict_code) REFERENCES public.sys_dict(code) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict 2DW06DgDie3lCSkHqUrWZPxAad8bl9T3eRfuignMYDBOqT8GE0wSCNrzqonZy4R

