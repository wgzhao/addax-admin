--
-- PostgreSQL database dump
--

\restrict i4g0vrBPcLJ3HlM80zOMR8urxeKakf70KqxUbKftoZTTzQHBR7BHXcm5N83E2sA

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

--
-- Name: fn_etl_table_record_change(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fn_etl_table_record_change() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ declare old_row jsonb; new_row jsonb; changed_fields jsonb; old_values jsonb; new_values jsonb; begin old_row := to_jsonb(old) - 'status' - 'duration' - 'retry_cnt' - 'start_time' - 'end_time' - 'created_at' - 'updated_at' - 'max_runtime' - 'kind'; new_row := to_jsonb(new) - 'status' - 'duration' - 'retry_cnt' - 'start_time' - 'end_time' - 'created_at' - 'updated_at' - 'max_runtime' - 'kind'; select coalesce(jsonb_agg(n.key order by n.key), '[]'::jsonb) into changed_fields from jsonb_each(new_row) n join jsonb_each(old_row) o on o.key = n.key where o.value is distinct from n.value; if changed_fields = '[]'::jsonb then return new; end if; select coalesce(jsonb_object_agg(field_name, old_row -> field_name), '{}'::jsonb), coalesce(jsonb_object_agg(field_name, new_row -> field_name), '{}'::jsonb) into old_values, new_values from jsonb_array_elements_text(changed_fields) as fields(field_name); insert into public.etl_table_change_log (tid, source_db, source_table, target_db, target_table, changed_fields, old_values, new_values, changed_by) values (new.id, new.source_db, new.source_table, new.target_db, new.target_table, changed_fields, old_values, new_values, nullif(current_setting('addax.changed_by', true), '')); return new; end; $$;


--
-- Name: insert_dates_for_year(integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.insert_dates_for_year(p_year integer, p_dict_code integer DEFAULT 1021) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
  v_date varchar;
BEGIN
  FOR v_date IN
    SELECT DISTINCT to_char(generate_series(make_date(p_year,1,1), make_date(p_year,12,31), '1 day'::interval), 'YYYYmmdd')
    -- SELECT DISTINCT date_trunc('day', generate_series('2025-01-01'::date, '2025-12-31'::date, '1 day'))
    LOOP
      INSERT INTO public.sys_item (dict_code, item_key, item_value, remark)
      VALUES (p_dict_code, v_date, v_date, NOW());
    END LOOP;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: addax_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.addax_log (
    id bigint NOT NULL,
    tid bigint NOT NULL,
    run_at timestamp without time zone,
    run_date date,
    log text
);


--
-- Name: addax_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.addax_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: addax_log_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.addax_log_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: addax_log_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.addax_log_id_seq1 OWNED BY public.addax_log.id;


--
-- Name: authorities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.authorities (
    username character varying(50) NOT NULL,
    authority character varying(50) NOT NULL
);


--
-- Name: etl_column; Type: TABLE; Schema: public; Owner: -
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


--
-- Name: TABLE etl_column; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_column IS '采集的表字段信息，包括源表和目标表';


--
-- Name: COLUMN etl_column.tid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.tid IS '采集表主键 ID，对应 tb_etl_table 中的 tid';


--
-- Name: COLUMN etl_column.column_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.column_name IS '原表字段名称';


--
-- Name: COLUMN etl_column.column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.column_id IS '列 ID，用于排序字段';


--
-- Name: COLUMN etl_column.source_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.source_type IS '源表的数据类型';


--
-- Name: COLUMN etl_column.data_length; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.data_length IS '数据长度';


--
-- Name: COLUMN etl_column.data_precision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.data_precision IS '精度';


--
-- Name: COLUMN etl_column.data_scale; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.data_scale IS '小数位';


--
-- Name: COLUMN etl_column.col_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.col_comment IS '字段注释';


--
-- Name: COLUMN etl_column.target_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.target_type IS '目标表对应的类型';


--
-- Name: COLUMN etl_column.target_type_full; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.target_type_full IS '目标表字段的完整类型，比如 decimal(10,3)';


--
-- Name: COLUMN etl_column.update_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_column.update_at IS '更新时间';


--
-- Name: etl_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_job (
    tid bigint NOT NULL,
    job text NOT NULL
);


--
-- Name: TABLE etl_job; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_job IS '采集表的 addax 任务模板';


--
-- Name: COLUMN etl_job.tid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_job.tid IS '采集表主键';


--
-- Name: COLUMN etl_job.job; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_job.job IS 'addax 任务模板';


--
-- Name: etl_job_queue; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_job_queue (
    id bigint NOT NULL,
    tid bigint NOT NULL,
    biz_date date NOT NULL,
    part_name character varying(64),
    payload jsonb,
    priority integer DEFAULT 100 NOT NULL,
    status character varying(16) DEFAULT 'pending'::character varying NOT NULL,
    available_at timestamp with time zone DEFAULT now() NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 3 NOT NULL,
    claimed_by character varying(128),
    claimed_at timestamp with time zone,
    lease_until timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT etl_job_queue_status_chk CHECK (((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('running'::character varying)::text, ('completed'::character varying)::text, ('failed'::character varying)::text, ('cancelled'::character varying)::text])))
);


--
-- Name: etl_job_queue_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_job_queue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_job_queue_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_job_queue_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_job_queue_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.etl_job_queue_id_seq1 OWNED BY public.etl_job_queue.id;


--
-- Name: etl_jour; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_jour (
    id bigint NOT NULL,
    tid bigint,
    kind character varying(32),
    start_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    duration integer DEFAULT 0 NOT NULL,
    status boolean DEFAULT true,
    cmd text,
    error_msg text
);


--
-- Name: etl_jour_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_jour_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_jour_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_jour_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_jour_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.etl_jour_id_seq1 OWNED BY public.etl_jour.id;


--
-- Name: etl_source; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_source (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    url character varying(500) NOT NULL,
    username character varying(64),
    pass character varying(64),
    start_at time without time zone,
    prerequisite character varying(4000),
    pre_script character varying(4000),
    remark character varying(2000),
    enabled boolean DEFAULT true,
    max_concurrency integer DEFAULT 5 NOT NULL,
    db_type character varying(20)
);


--
-- Name: TABLE etl_source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_source IS '采集源表';


--
-- Name: COLUMN etl_source.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.id IS '采集源 ID';


--
-- Name: COLUMN etl_source.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.code IS '采集编号,一般以两个大写字母作为标志';


--
-- Name: COLUMN etl_source.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.name IS '采集源名称';


--
-- Name: COLUMN etl_source.url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.url IS '采集源 JDBC 连接串';


--
-- Name: COLUMN etl_source.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.username IS '采集源连接的账号';


--
-- Name: COLUMN etl_source.pass; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.pass IS '采集源连接的密码';


--
-- Name: COLUMN etl_source.start_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.start_at IS '采集的定时启动时间点，一般只考虑到小时和分钟，秒钟默认为 0';


--
-- Name: COLUMN etl_source.prerequisite; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.prerequisite IS '能否开始采集的先决条件，比如获取采集标志位，或者等待数据不再更新，一般是一段 SQL，然后通过返回值真假进行判断';


--
-- Name: COLUMN etl_source.pre_script; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.pre_script IS '标志符合条件后的前置脚本';


--
-- Name: COLUMN etl_source.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.remark IS '系统备注信息';


--
-- Name: COLUMN etl_source.enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.enabled IS '是否有效';


--
-- Name: COLUMN etl_source.max_concurrency; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.max_concurrency IS '采集源允许的最大并发数';


--
-- Name: COLUMN etl_source.db_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_source.db_type IS '数据库类型';


--
-- Name: etl_source_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_source_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_source_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_source_id_seq1
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_source_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.etl_source_id_seq1 OWNED BY public.etl_source.id;


--
-- Name: etl_statistic; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_statistic (
    id bigint NOT NULL,
    tid bigint,
    start_at timestamp without time zone,
    end_at timestamp without time zone,
    take_secs integer,
    total_bytes bigint,
    byte_speed integer,
    rec_speed integer,
    total_recs bigint,
    total_errors integer,
    biz_date date
);


--
-- Name: TABLE etl_statistic; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_statistic IS '采集统计表';


--
-- Name: COLUMN etl_statistic.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.id IS '逻辑主键';


--
-- Name: COLUMN etl_statistic.tid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.tid IS '采集表主键';


--
-- Name: COLUMN etl_statistic.start_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.start_at IS '采集开始时间';


--
-- Name: COLUMN etl_statistic.end_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.end_at IS '采集结束时间';


--
-- Name: COLUMN etl_statistic.take_secs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.take_secs IS '采集耗时';


--
-- Name: COLUMN etl_statistic.total_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.total_bytes IS '采集的总字节数';


--
-- Name: COLUMN etl_statistic.byte_speed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.byte_speed IS '采集速度  字节/秒';


--
-- Name: COLUMN etl_statistic.rec_speed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.rec_speed IS '采集速度 行/秒';


--
-- Name: COLUMN etl_statistic.total_recs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.total_recs IS '采集的总行数';


--
-- Name: COLUMN etl_statistic.total_errors; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.total_errors IS '采集时发生错误的行数';


--
-- Name: COLUMN etl_statistic.biz_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_statistic.biz_date IS '采集业务日期';


--
-- Name: etl_statistic_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_statistic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_statistic_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.etl_statistic ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.etl_statistic_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: tb_imp_etl_tid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tb_imp_etl_tid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_table; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_table (
    id bigint DEFAULT nextval('public.tb_imp_etl_tid_seq'::regclass) NOT NULL,
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
    status character(1) DEFAULT 'U'::bpchar NOT NULL,
    split_pk character varying(50) DEFAULT NULL::character varying,
    auto_pk boolean DEFAULT true NOT NULL,
    write_mode character varying(20) DEFAULT 'overwrite'::character varying NOT NULL,
    start_at time without time zone,
    target_id integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reader_plugin_config jsonb,
    writer_plugin_config jsonb
);


--
-- Name: TABLE etl_table; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_table IS '采集表信息';


--
-- Name: COLUMN etl_table.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.id IS '表 ID';


--
-- Name: COLUMN etl_table.source_db; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.source_db IS '采集源库名或 schema名称或 owner 名称';


--
-- Name: COLUMN etl_table.source_table; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.source_table IS '采集源表名';


--
-- Name: COLUMN etl_table.target_db; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.target_db IS '目标库名，即提供给 hive 的库名';


--
-- Name: COLUMN etl_table.target_table; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.target_table IS '目标表名，即 Hive 的表名';


--
-- Name: COLUMN etl_table.part_kind; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.part_kind IS '分区类型，D - 按每日分区，如果为空，则表示不分区';


--
-- Name: COLUMN etl_table.part_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.part_name IS '目标表分区字段名称，如果 dest_part_kind 不为空，则该字段也不能为空';


--
-- Name: COLUMN etl_table.filter; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.filter IS '采集过滤条件，即 where 条件';


--
-- Name: COLUMN etl_table.kind; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.kind IS '采集类型: A - 自动采集(默认值); M - 手工采集; R - 实时采集';


--
-- Name: COLUMN etl_table.retry_cnt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.retry_cnt IS '采集的重试次数，用于采集失败时，可以多次尝试';


--
-- Name: COLUMN etl_table.start_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.start_time IS '本次采集的开始时间';


--
-- Name: COLUMN etl_table.end_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.end_time IS '本次采集的结束时间';


--
-- Name: COLUMN etl_table.max_runtime; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.max_runtime IS '采集可只持续的最大时间，避免某些采集因为网络或数据源原因一直无法结束';


--
-- Name: COLUMN etl_table.sid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.sid IS '采集源 ID，对应 etl_source 表 id';


--
-- Name: COLUMN etl_table.duration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.duration IS '采集耗时，单位为秒';


--
-- Name: COLUMN etl_table.part_format; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.part_format IS '分区字段日期格式';


--
-- Name: COLUMN etl_table.storage_format; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.storage_format IS '压缩格式，可以是snappy,zlib,lz4,gz,bz2,zstd 等';


--
-- Name: COLUMN etl_table.tbl_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.tbl_comment IS '表注释';


--
-- Name: COLUMN etl_table.split_pk; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.split_pk IS '切分主键';


--
-- Name: COLUMN etl_table.auto_pk; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.auto_pk IS '自动获取切分字段';


--
-- Name: COLUMN etl_table.write_mode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.write_mode IS '覆盖默认，默认为 overwrite，可选为 append,nonConflict';


--
-- Name: COLUMN etl_table.start_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.start_at IS '表级采集定时启动时间点；为空表示继承 etl_source.start_at';


--
-- Name: COLUMN etl_table.reader_plugin_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.reader_plugin_config IS '读取插件自定义配置(JSON)，将合并到 reader.parameter';


--
-- Name: COLUMN etl_table.writer_plugin_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table.writer_plugin_config IS '写入插件自定义配置(JSON)，将合并到 writer.parameter';


--
-- Name: etl_table_change_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_table_change_log (
    id bigint NOT NULL,
    tid bigint NOT NULL,
    source_db character varying(32),
    source_table character varying(64),
    target_db character varying(50),
    target_table character varying(200),
    changed_fields jsonb NOT NULL,
    old_values jsonb NOT NULL,
    new_values jsonb NOT NULL,
    changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    changed_by character varying(100)
);


--
-- Name: TABLE etl_table_change_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_table_change_log IS '采集表配置变更历史';


--
-- Name: COLUMN etl_table_change_log.tid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table_change_log.tid IS '采集表ID，对应 etl_table.id';


--
-- Name: COLUMN etl_table_change_log.changed_fields; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table_change_log.changed_fields IS '本次变更的字段名列表';


--
-- Name: COLUMN etl_table_change_log.old_values; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table_change_log.old_values IS '本次变更前的字段值';


--
-- Name: COLUMN etl_table_change_log.new_values; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table_change_log.new_values IS '本次变更后的字段值';


--
-- Name: COLUMN etl_table_change_log.changed_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_table_change_log.changed_by IS '触发本次变更的用户';


--
-- Name: etl_table_change_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_table_change_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_table_change_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.etl_table_change_log_id_seq OWNED BY public.etl_table_change_log.id;


--
-- Name: etl_target; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.etl_target (
    id integer NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    target_type character varying(30) NOT NULL,
    connect_config text,
    writer_template_key character varying(32),
    enabled boolean DEFAULT true NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    remark character varying(500)
);


--
-- Name: TABLE etl_target; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.etl_target IS '目标端配置表';


--
-- Name: COLUMN etl_target.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.id IS '目标端主键ID';


--
-- Name: COLUMN etl_target.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.code IS '目标端编码';


--
-- Name: COLUMN etl_target.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.name IS '目标端名称';


--
-- Name: COLUMN etl_target.target_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.target_type IS '目标端类型（HIVE/MYSQL/POSTGRESQL等）';


--
-- Name: COLUMN etl_target.connect_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.connect_config IS '连接配置JSON';


--
-- Name: COLUMN etl_target.writer_template_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.writer_template_key IS 'writer模板键';


--
-- Name: COLUMN etl_target.enabled; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.enabled IS '是否启用';


--
-- Name: COLUMN etl_target.is_default; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.is_default IS '是否默认目标端';


--
-- Name: COLUMN etl_target.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.etl_target.remark IS '备注';


--
-- Name: etl_target_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.etl_target_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: etl_target_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.etl_target_id_seq OWNED BY public.etl_target.id;


--
-- Name: group_authorities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.group_authorities (
    group_id bigint NOT NULL,
    authority character varying(50) NOT NULL
);


--
-- Name: group_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.group_members (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    group_id bigint NOT NULL
);


--
-- Name: group_members_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.group_members_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: group_members_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.group_members ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.group_members_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.groups (
    id bigint NOT NULL,
    group_name character varying(50) NOT NULL
);


--
-- Name: groups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.groups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: groups_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.groups ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.groups_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: leader_election; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leader_election (
    id integer,
    node_id character varying(100),
    expires_at timestamp with time zone,
    updated_at timestamp with time zone
);


--
-- Name: TABLE leader_election; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.leader_election IS 'leader 选举表';


--
-- Name: COLUMN leader_election.node_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.leader_election.node_id IS '节点ID';


--
-- Name: COLUMN leader_election.expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.leader_election.expires_at IS '过期时间';


--
-- Name: COLUMN leader_election.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.leader_election.updated_at IS '更新时间';


--
-- Name: leader_election_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.leader_election_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
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


--
-- Name: TABLE notification; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.notification IS '数据中心消息提醒总表';


--
-- Name: COLUMN notification.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.id IS '自动生成，无需理会';


--
-- Name: COLUMN notification.phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.phone IS '接收人号码或者其他唯一标识，用逗号分隔';


--
-- Name: COLUMN notification.msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.msg IS '消息内容';


--
-- Name: COLUMN notification.sms; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.sms IS '是否发送短信，发送成功后置为y';


--
-- Name: COLUMN notification.im; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.im IS '是否发送企微，发送成功后置为y';


--
-- Name: COLUMN notification.call; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.call IS '是否拨打语音，拨打成功后置为y';


--
-- Name: COLUMN notification.create_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.notification.create_at IS '消息生成的时间，自动生成';


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_id_seq1 OWNED BY public.notification.id;


--
-- Name: risk_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_log (
    id bigint NOT NULL,
    risk_level character varying(30) NOT NULL,
    source character varying(30),
    message character varying(1000),
    tid integer,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE risk_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.risk_log IS '风险记录表';


--
-- Name: COLUMN risk_log.risk_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.risk_log.risk_level IS '风险级别';


--
-- Name: COLUMN risk_log.source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.risk_log.source IS '风险来源';


--
-- Name: COLUMN risk_log.message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.risk_log.message IS '风险摘要';


--
-- Name: COLUMN risk_log.tid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.risk_log.tid IS '关联表 ID';


--
-- Name: COLUMN risk_log.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.risk_log.created_at IS '创建时间';


--
-- Name: risk_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.risk_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: risk_log_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.risk_log_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: risk_log_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.risk_log_id_seq1 OWNED BY public.risk_log.id;


--
-- Name: schema_change_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_change_log (
    id bigint NOT NULL,
    tid bigint NOT NULL,
    source_db character varying(64),
    source_table character varying(128),
    column_name character varying(255),
    change_type character varying(32),
    old_source_type character varying(128),
    new_source_type character varying(128),
    old_data_length integer,
    new_data_length integer,
    old_data_precision integer,
    new_data_precision integer,
    old_data_scale integer,
    new_data_scale integer,
    old_col_comment character varying(2000),
    new_col_comment character varying(2000),
    change_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: schema_change_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.schema_change_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schema_change_log_id_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.schema_change_log_id_seq1
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: schema_change_log_id_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.schema_change_log_id_seq1 OWNED BY public.schema_change_log.id;


--
-- Name: sys_dict; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_dict (
    code integer NOT NULL,
    name character varying(255),
    classification character varying(2000),
    remark character varying(500)
);


--
-- Name: TABLE sys_dict; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_dict IS '字典条目表';


--
-- Name: COLUMN sys_dict.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict.code IS '条目编号';


--
-- Name: COLUMN sys_dict.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict.name IS '条目名称';


--
-- Name: COLUMN sys_dict.classification; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict.classification IS '分类';


--
-- Name: COLUMN sys_dict.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_dict.remark IS '说明';


--
-- Name: sys_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_item (
    dict_code integer NOT NULL,
    item_key character varying(255) NOT NULL,
    item_value character varying(2000),
    remark character varying(4000)
);


--
-- Name: TABLE sys_item; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.sys_item IS '字典明细表';


--
-- Name: COLUMN sys_item.dict_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_item.dict_code IS '字典条目编号';


--
-- Name: COLUMN sys_item.item_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_item.item_key IS '明细名称';


--
-- Name: COLUMN sys_item.item_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_item.item_value IS '明细内容';


--
-- Name: COLUMN sys_item.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_item.remark IS '备注';


--
-- Name: system_flag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_flag (
    flag_key character varying(128) NOT NULL,
    flag_value character varying(128) DEFAULT '0'::character varying,
    last_started_at timestamp without time zone,
    last_finished_at timestamp without time zone,
    updated_at timestamp without time zone,
    updated_by character varying(255)
);


--
-- Name: tb_addax_statistic_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tb_addax_statistic_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_notification (
    id bigint NOT NULL,
    username character varying(64) NOT NULL,
    title character varying(200) NOT NULL,
    content text,
    type character varying(50),
    status character varying(16) DEFAULT 'UNREAD'::character varying NOT NULL,
    ref_type character varying(50),
    ref_id character varying(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    read_at timestamp without time zone
);


--
-- Name: user_notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_notification_id_seq OWNED BY public.user_notification.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    username character varying(50) NOT NULL,
    password character varying(500) NOT NULL,
    enabled boolean NOT NULL
);


--
-- Name: vw_etl_table_with_source; Type: VIEW; Schema: public; Owner: -
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
    t.split_pk,
    t.auto_pk,
    t.write_mode,
    t.start_at,
    t.target_id,
    t.created_at,
    t.updated_at,
    t.reader_plugin_config,
    t.writer_plugin_config,
    s.code,
    s.name,
    s.url,
    s.username,
    s.pass,
    s.start_at AS source_start_at,
    s.enabled,
    s.max_concurrency,
    s.db_type,
    tt.target_type,
    tt.code AS target_code,
    tt.name AS target_name,
    tt.enabled AS target_enabled
   FROM ((public.etl_table t
     LEFT JOIN public.etl_source s ON ((t.sid = s.id)))
     LEFT JOIN public.etl_target tt ON ((t.target_id = tt.id)));


--
-- Name: addax_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addax_log ALTER COLUMN id SET DEFAULT nextval('public.addax_log_id_seq1'::regclass);


--
-- Name: etl_job_queue id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_job_queue ALTER COLUMN id SET DEFAULT nextval('public.etl_job_queue_id_seq1'::regclass);


--
-- Name: etl_jour id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_jour ALTER COLUMN id SET DEFAULT nextval('public.etl_jour_id_seq1'::regclass);


--
-- Name: etl_source id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_source ALTER COLUMN id SET DEFAULT nextval('public.etl_source_id_seq1'::regclass);


--
-- Name: etl_table_change_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_table_change_log ALTER COLUMN id SET DEFAULT nextval('public.etl_table_change_log_id_seq'::regclass);


--
-- Name: etl_target id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_target ALTER COLUMN id SET DEFAULT nextval('public.etl_target_id_seq'::regclass);


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification ALTER COLUMN id SET DEFAULT nextval('public.notification_id_seq1'::regclass);


--
-- Name: risk_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_log ALTER COLUMN id SET DEFAULT nextval('public.risk_log_id_seq1'::regclass);


--
-- Name: schema_change_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_change_log ALTER COLUMN id SET DEFAULT nextval('public.schema_change_log_id_seq1'::regclass);


--
-- Name: user_notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notification ALTER COLUMN id SET DEFAULT nextval('public.user_notification_id_seq'::regclass);


--
-- Name: addax_log addax_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addax_log
    ADD CONSTRAINT addax_log_pkey PRIMARY KEY (id);


--
-- Name: etl_job_queue etl_job_queue_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_job_queue
    ADD CONSTRAINT etl_job_queue_pkey PRIMARY KEY (id);


--
-- Name: etl_jour etl_jour_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_jour
    ADD CONSTRAINT etl_jour_pkey PRIMARY KEY (id);


--
-- Name: etl_source etl_source_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_source
    ADD CONSTRAINT etl_source_pkey PRIMARY KEY (id);


--
-- Name: etl_statistic etl_statistic_tid_biz_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_statistic
    ADD CONSTRAINT etl_statistic_tid_biz_date UNIQUE (tid, biz_date);


--
-- Name: etl_table_change_log etl_table_change_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_table_change_log
    ADD CONSTRAINT etl_table_change_log_pkey PRIMARY KEY (id);


--
-- Name: etl_target etl_target_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_target
    ADD CONSTRAINT etl_target_pkey PRIMARY KEY (id);


--
-- Name: group_members group_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT group_members_pkey PRIMARY KEY (id);


--
-- Name: groups groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.groups
    ADD CONSTRAINT groups_pkey PRIMARY KEY (id);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: sys_dict pk_tb_dict; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_dict
    ADD CONSTRAINT pk_tb_dict PRIMARY KEY (code);


--
-- Name: sys_item pk_tb_dictionary; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_item
    ADD CONSTRAINT pk_tb_dictionary PRIMARY KEY (dict_code, item_key);


--
-- Name: etl_job pk_tb_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_job
    ADD CONSTRAINT pk_tb_job PRIMARY KEY (tid);


--
-- Name: schema_change_log schema_change_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_change_log
    ADD CONSTRAINT schema_change_log_pkey PRIMARY KEY (id);


--
-- Name: system_flag system_flag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_flag
    ADD CONSTRAINT system_flag_pkey PRIMARY KEY (flag_key);


--
-- Name: etl_statistic tb_addax_statistic_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_statistic
    ADD CONSTRAINT tb_addax_statistic_pkey PRIMARY KEY (id);


--
-- Name: etl_table tb_imp_etl_pkey1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_table
    ADD CONSTRAINT tb_imp_etl_pkey1 PRIMARY KEY (id);


--
-- Name: user_notification user_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notification
    ADD CONSTRAINT user_notification_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (username);


--
-- Name: idx_etl_job_queue_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_job_queue_lease ON public.etl_job_queue USING btree (status, lease_until);


--
-- Name: idx_etl_job_queue_pending_priority_available; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_job_queue_pending_priority_available ON public.etl_job_queue USING btree (priority, available_at, id) WHERE ((status)::text = 'pending'::text);


--
-- Name: idx_etl_job_queue_running_lease_until; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_job_queue_running_lease_until ON public.etl_job_queue USING btree (lease_until) WHERE ((status)::text = 'running'::text);


--
-- Name: idx_etl_job_queue_status_available; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_job_queue_status_available ON public.etl_job_queue USING btree (status, available_at, priority);


--
-- Name: idx_etl_jour_tid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_jour_tid ON public.etl_jour USING btree (tid);


--
-- Name: idx_etl_table_change_log_tid_changed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_table_change_log_tid_changed_at ON public.etl_table_change_log USING btree (tid, changed_at DESC, id DESC);


--
-- Name: idx_etl_table_status_id_sid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_etl_table_status_id_sid ON public.etl_table USING btree (status, id, sid);


--
-- Name: idx_tid_run_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tid_run_date ON public.addax_log USING btree (tid, run_at);


--
-- Name: idx_user_notification_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_notification_user_status ON public.user_notification USING btree (username, status, created_at);


--
-- Name: ix_auth_username; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ix_auth_username ON public.authorities USING btree (username, authority);


--
-- Name: uk_etl_target_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_etl_target_code ON public.etl_target USING btree (code);


--
-- Name: uk_tid_column_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_tid_column_name ON public.etl_column USING btree (tid, column_name);


--
-- Name: uniq_active_etl_job; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uniq_active_etl_job ON public.etl_job_queue USING btree (tid, biz_date) WHERE ((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('running'::character varying)::text]));


--
-- Name: etl_table trg_etl_table_record_change; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_etl_table_record_change AFTER UPDATE ON public.etl_table FOR EACH ROW EXECUTE FUNCTION public.fn_etl_table_record_change();


--
-- Name: etl_column etl_column_tid_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_column
    ADD CONSTRAINT etl_column_tid_fk FOREIGN KEY (tid) REFERENCES public.etl_table(id);


--
-- Name: etl_job etl_job_tid_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_job
    ADD CONSTRAINT etl_job_tid_fk FOREIGN KEY (tid) REFERENCES public.etl_table(id);


--
-- Name: etl_jour etl_jour_tid_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_jour
    ADD CONSTRAINT etl_jour_tid_fk FOREIGN KEY (tid) REFERENCES public.etl_table(id);


--
-- Name: etl_table etl_table_sid_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.etl_table
    ADD CONSTRAINT etl_table_sid_fk FOREIGN KEY (sid) REFERENCES public.etl_source(id);


--
-- Name: authorities fk_authorities_users; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.authorities
    ADD CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES public.users(username);


--
-- Name: group_authorities fk_group_authorities_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_authorities
    ADD CONSTRAINT fk_group_authorities_group FOREIGN KEY (group_id) REFERENCES public.groups(id);


--
-- Name: group_members fk_group_members_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_members
    ADD CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES public.groups(id);


--
-- Name: sys_item tb_item_dict_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_item
    ADD CONSTRAINT tb_item_dict_fk FOREIGN KEY (dict_code) REFERENCES public.sys_dict(code) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict i4g0vrBPcLJ3HlM80zOMR8urxeKakf70KqxUbKftoZTTzQHBR7BHXcm5N83E2sA

