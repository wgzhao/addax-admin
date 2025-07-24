-- PostgreSQL View Definitions migrated from Oracle
-- Migration includes: FORCE removal, DECODE->CASE, NVL->COALESCE, regex functions adjustment

CREATE OR REPLACE VIEW vw_ci_deploy AS
SELECT t.dep_id,
       t.proj_name,
       t.file_path,
       t.file_name,
       t.file_content,
       t.file_md5,
       t.updt_date,
       regexp_replace(lower(t.file_name), '\.sql$', '', 'g') as spname,
       CASE WHEN lower(t.file_name) ~ '^[a-z0-9_]+\.[a-z0-9_]+\.sql$' THEN 1 ELSE 0 END as bvalid
FROM tb_ci_deploy t
INNER JOIN vw_ci_deploy_id a
   ON a.dep_id = t.dep_id
  AND a.next_dep_id IS NULL;

CREATE OR REPLACE VIEW vw_ci_deploy_id AS
SELECT dep_id,
       max_date as dep_date,
       lag(dep_id, 1) OVER(PARTITION BY proj_name ORDER BY max_date) as last_dep_id,
       lead(dep_id, 1) OVER(PARTITION BY proj_name ORDER BY max_date) as next_dep_id
FROM (SELECT 'xx' as proj_name, dep_id, max(updt_date) as max_date
      FROM tb_ci_deploy
      GROUP BY dep_id) t;

CREATE OR REPLACE VIEW vw_imp_check_soutab AS
SELECT COALESCE(t.sou_db_conn, a.sou_db_conn) as sou_db_conn,
       COALESCE(t.owner, a.owner) as owner,
       COALESCE(t.table_name, a.table_name) as table_name,
       COALESCE(t.column_name, a.column_name) as column_name,
       t.data_type,
       a.data_type as data_type_last,
       (SELECT hive_type FROM vw_imp_etl_coltype WHERE coltype=upper(t.data_type)) as hive_type,
       (SELECT hive_type FROM vw_imp_etl_coltype WHERE coltype=upper(a.data_type)) as hive_type_last,
       t.data_length,
       a.data_length as data_length_last,
       t.data_precision,
       a.data_precision as data_precision_last,
       t.data_scale,
       a.data_scale as data_scale_last,
       t.dw_clt_date,
       a.dw_clt_date as dw_clt_date_last
FROM tb_imp_etl_soutab t
FULL JOIN backup.v_stg01_tb_imp_etl_soutab a
   ON a.sou_db_conn = t.sou_db_conn
  AND a.owner = t.owner
  AND a.table_name = t.table_name
  AND a.column_name = t.column_name
WHERE COALESCE(t.column_name, '') <> COALESCE(a.column_name, '')
   OR t.data_type <> a.data_type
   OR t.data_length <> a.data_length
   OR t.data_precision <> a.data_precision
   OR t.data_scale <> a.data_scale;

CREATE OR REPLACE VIEW vw_imp_chk_inf AS
WITH t_inf AS ( -- 前端自定义的检测项
    SELECT entry_value as chk_idx,
           regexp_replace(substring(entry_content from '\[S[^]]+'), '^\[S', '', 'g') as chk_sendtype,
           regexp_replace(substring(entry_content from '\[M[^]]+'), '^\[M', '', 'g') as chk_mobile,
           COALESCE(regexp_replace(substring(entry_content from '\[P[^]]+'), '^\[P', '', 'g'), '3') as chk_pntype,
           substring(entry_content from '[^]]+$') as chk_kind,
           remark as chk_sql,
           CASE entry_code WHEN '1073' THEN 'allsql' WHEN '1074' THEN 'ini' END as engine
    FROM tb_dictionary
    WHERE entry_code IN('1073','1074')
      AND remark IS NOT NULL
      AND entry_content NOT LIKE '%[x]%'
),
t_tbl_range AS ( -- 核心主表：需要检查的表及字段
    SELECT t.owner,
           t.table_name,
           t.column_name,
           dense_rank() OVER(PARTITION BY owner,table_name ORDER BY COALESCE(substr(a.entry_value, 1, 1), ' ') DESC) as pk_lev
    FROM information_schema.columns t
    LEFT JOIN tb_dictionary a
      ON a.entry_code = '1077'
     AND a.entry_content = t.column_name
    WHERE t.table_schema = 'stg01'
      AND t.table_name IN (SELECT entry_value FROM tb_dictionary WHERE entry_code = '1075')
      AND t.column_name NOT IN (SELECT entry_value FROM tb_dictionary WHERE entry_code = '1076')
),
t_tbl_has2 AS ( -- 核心主表：检查字段在备份表中是否存在
    SELECT CASE WHEN table_schema='stg01' THEN table_name
                ELSE regexp_replace(table_name, '^V_[^_]+_', '', 'g')
           END as table_name,
           column_name
    FROM information_schema.columns t
    WHERE data_type IN ('character varying', 'numeric', 'character')
      AND (table_schema = 'stg01' OR (table_schema = 'backup' AND table_name ~ '^V_STG01_'))
    GROUP BY CASE WHEN table_schema='stg01' THEN table_name
                  ELSE regexp_replace(table_name, '^V_[^_]+_', '', 'g')
             END, column_name
    HAVING count(1) = 2
),
t_tbl AS ( -- 针对配置主表内容变更的额外检测项，在自定义etl004的基础上新增检测项
    SELECT t.owner, t.table_name, t.column_name, c.column_name as pk,
           COALESCE(substring(comments from '^[^,，（:：]+'), t.column_name) as comments
    FROM t_tbl_range t
    INNER JOIN t_tbl_has2 a ON a.table_name = t.table_name AND a.column_name = t.column_name
    LEFT JOIN (SELECT table_schema as owner, table_name, column_name,
                      obj_description(pgc.oid) as comments
               FROM information_schema.columns ic
               JOIN pg_class pgc ON pgc.relname = ic.table_name
               WHERE table_schema = 'stg01') b
       ON b.owner = t.owner AND b.table_name = t.table_name AND b.column_name = t.column_name
    LEFT JOIN t_tbl_range c ON c.pk_lev=1 AND c.owner = t.owner AND c.table_name = t.table_name
),
t_all AS ( -- 自定义配置项+配置主表内容变更
    SELECT * FROM t_inf WHERE chk_idx<>'etl004'
    UNION ALL
    SELECT a.chk_idx, a.chk_sendtype, a.chk_mobile, '3', a.chk_kind,
           'select ''' || table_name || ''' chk_name,fn_imp_value(''taskname'',t.' || pk || ')||''的' ||
           column_name || '[' || comments || ']' || ':''||substr(a.' || column_name || ',1,200)||''=>''||substr(t.' || column_name ||
           ',1,200) chk_content from ' || table_name || ' t inner join backup.v_stg01_' || table_name ||
           ' a on a.' || pk || '=t.' || pk || ' where COALESCE(t.' || column_name || ',0)<>COALESCE(a.' || column_name || ',0)', 'ini'
    FROM t_tbl t
    INNER JOIN t_inf a ON a.chk_idx='etl004'
    WHERE t.column_name <> t.pk
    UNION ALL
    SELECT a.chk_idx, a.chk_sendtype, a.chk_mobile, '3', a.chk_kind, t.chk_sql, 'ini'
    FROM (
        SELECT 'select ''' || table_name || ''' chk_name,fn_imp_value(''taskname'',COALESCE(t.' || pk || ',a.' || pk || ')) || ' ||
               'case when t.' || pk || ' is null then ''今日被删除---'' when a.' || pk || ' is null then ''今日新增+++'' end chk_content ' ||
               'from ' || owner || '.' || table_name || ' t full join backup.v_' || owner || '_' || table_name ||
               ' a on a.' || pk || '=t.' || pk || ' where t.' || pk || ' is null or a.' || pk || ' is null' as chk_sql
        FROM t_tbl
        GROUP BY owner, table_name, pk
    ) t
    INNER JOIN t_inf a ON a.chk_idx='etl004'
)
SELECT engine,
       chk_idx,
       chk_sendtype,
       chk_mobile,
       fn_imp_pntype(chk_pntype) as bpntype,
       chk_kind,
       'select ''' || COALESCE(chk_mobile, '1') || ''' chk_mobile,''' ||
       chk_sendtype || ''' chk_sendtype,''' || chk_kind ||
       ''' chk_kind,chk_name,chk_content from (' || chk_sql || ')' as chk_sql
FROM t_all;

CREATE OR REPLACE VIEW vw_imp_date AS
SELECT entry_value::numeric as dt, entry_content as dt_full
FROM tb_dictionary
WHERE entry_code='1022';

CREATE OR REPLACE VIEW vw_imp_db AS
SELECT t.db_name,
       t.db_constr,
       t.db_id_etl,
       CASE WHEN t.db_id_etl IS NOT NULL THEN COALESCE(t.db_user_etl,t.db_user_ds) END as db_user_etl,
       CASE WHEN t.db_id_etl IS NOT NULL THEN COALESCE(t.db_pass_etl,t.db_pass_ds) END as db_pass_etl,
       t.db_paral_etl,
       t.db_id_ds,
       CASE WHEN t.db_id_ds IS NOT NULL THEN COALESCE(t.db_user_ds,t.db_user_etl) END as db_user_ds,
       CASE WHEN t.db_id_ds IS NOT NULL THEN COALESCE(t.db_pass_ds,t.db_pass_etl) END as db_pass_ds,
       t.db_paral_ds,
       t.db_start,
       t.db_start_type,
       t.db_judge_sql,
       t.db_judge_pre,
       t.db_remark,
       t.did,
       t.bvalid,
       db_name || CASE WHEN bvalid = 'N' THEN '_已废弃' ELSE '' END as sys_name,
       CASE WHEN bvalid = 'Y' THEN
            replace(
                COALESCE(
                    substring(db_constr from '([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]+'), -- 正常ip和端口的写法
                    replace(substring(db_constr from '//[^/]+'), '//', '') -- 域名的方式
                ), ':', ',')
       END as netchk,
       upper(substr(substring(db_constr from '^jdbc:.{1}'), -1)) as db_kind,
       replace(substring(db_constr from '^jdbc:[^:]+'), 'jdbc:', '') as db_kind_full,
       t.conf
FROM tb_imp_db t;

CREATE OR REPLACE VIEW vw_imp_ds2 AS
SELECT t.ds_id, a.ds_name, t.dest_sysid, t.task_group, t.param_sou, t.retry_cnt, t.run_freq,
       CASE WHEN t.flag<>'X' THEN 1 ELSE 0 END as bvalid,
       CASE WHEN t.flag='X' THEN 'X'
            ELSE CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.flag ELSE 'N' END
       END as flag,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.start_time END as start_time,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd AND t.end_time>=t.start_time THEN t.end_time END as end_time,
       t.runtime,
       CASE WHEN (flag='N' OR (flag='E' AND retry_cnt>0))
                 AND
                 (COALESCE(a.tbl_cnt,0)>0 OR a.db_kind_full='file') THEN 1
            ELSE 0 END as brun,
       CASE WHEN t.start_time < x.td AND current_timestamp > t.start_time + INTERVAL '1 day' * (
            CASE WHEN t.task_group ~ '^[A-Z0-9]{2}$' THEN
                 (gettd()::date - getltd()::date)
                 ELSE (getntd()::date - gettd()::date) END
            ) THEN 1 ELSE 0 END as bdelay,
       fn_imp_freqchk(run_freq) as bfreq,
       CASE WHEN pn_type IS NOT NULL AND (pn_fixed IS NOT NULL OR pn_interval IS NOT NULL) THEN 1 ELSE 0 END as bplan,
       CASE WHEN pre_sh IS NOT NULL THEN fn_imp_param_replace(pre_sh,param_sou) END as pre_sh,
       CASE WHEN post_sh IS NOT NULL THEN fn_imp_param_replace(post_sh,param_sou) END as post_sh,
       CASE WHEN COALESCE(pre_sql,'T') NOT IN('T','D') OR t.dest_sysid='TM' THEN
            a.d_conn_full || chr(10) ||
            CASE WHEN dest_sysid='TM' THEN a.pre_bak ELSE fn_imp_param_replace(pre_sql,param_sou) END
       END as pre_sql,
       CASE WHEN post_sql IS NOT NULL THEN
            a.d_conn_full || chr(10) ||
            replace(replace(fn_imp_param_replace(post_sql,param_sou),'${task_group}',task_group),
                   '${start_time}',to_char(start_time,'YYYY-MM-DD HH24:MI:SS'))
       END as post_sql,
       -- redis
       'rds "set ds.'||t.ds_id||'.paral_num '||COALESCE(t.paral_num,1)||'";'||
       CASE WHEN pre_sh IS NOT NULL THEN 'rds "set ds.'||t.ds_id||'.pre_sh 1";' ELSE '' END||
       CASE WHEN post_sh IS NOT NULL THEN 'rds "set ds.'||t.ds_id||'.post_sh 1";' ELSE '' END||
       CASE WHEN a.db_kind_full<>'file' THEN
         CASE WHEN t.bupdate='Y' THEN 'rds "set ds.'||t.ds_id||'.bupdate 1";' ELSE '' END||
         CASE WHEN COALESCE(has_dsview,0)=1 THEN 'rds "set ds.'||t.ds_id||'.dsview 1";' ELSE '' END ||
         CASE WHEN COALESCE(pre_sql,'T') NOT IN('T','D') OR a.pre_bak IS NOT NULL THEN 'rds "set ds.'||t.ds_id||'.pre_sql 1";' ELSE '' END||
         CASE WHEN post_sql IS NOT NULL THEN 'rds "set ds.'||t.ds_id||'.post_sql 1";' ELSE '' END
       ELSE '' END as init_rds
FROM tb_imp_ds2 t
INNER JOIN vw_imp_tradetime x ON 1=1
INNER JOIN (SELECT ds_id, db_kind_full, ds_name, d_conn_full,
                   max(CASE WHEN bvalid=1 AND sou_istab=0 AND flag='N' THEN 1 ELSE 0 END) as has_dsview,
                   sum(CASE WHEN bvalid=1 AND COALESCE(sou_table,dest_tablename) IS NOT NULL THEN 1 ELSE 0 END) as tbl_cnt,
                   string_agg(CASE WHEN bvalid=1 AND dest_sysid='TM' THEN
                             'create table ias_bak.'||dest_tablename||to_char(current_timestamp,'_MMDDHH24MISS')||
                             ' as select * from ias.'||dest_tablename END, chr(10)) as pre_bak
            FROM vw_imp_ds2_mid GROUP BY ds_id, db_kind_full, ds_name, d_conn_full
     ) a ON a.ds_id=t.ds_id;

CREATE OR REPLACE VIEW vw_imp_ds2_mid AS
SELECT t.ds_id,
       b.sys_name||'-'||substring(task_group from '^[^,]+') as ds_name,
       t.task_group,
       CASE WHEN a.sou_ishdp IN('1','2') THEN '1' ELSE '0' END as sou_ishdp,
       CASE WHEN a.sou_ishdp = '2' THEN 1 ELSE 0 END as sou_allsql,
       a.sou_table,
       a.sou_filter,
       CASE WHEN upper(a.sou_table) ~ '^[0-9A-Z_]+\.[0-9A-Z_]+$' THEN 1 ELSE 0 END as sou_istab,
       t.dest_sysid,
       b.sys_name as dest_sysname,
       COALESCE(a.col_map, t.col_map) as col_map,
       t.param_sou,
       t.retry_cnt,
       b.db_constr as d_conn,
       b.db_user as d_user,
       b.db_pass as d_pass,
       b.db_user||chr(10)||b.db_pass||chr(10)||b.db_constr as d_conn_full,
       b.db_kind,
       b.db_kind_full,
       COALESCE(a.dest_owner, t.dest_owner, b.db_user,replace(b.db_constr,'jdbc:file:','')) as dest_owner,
       a.dest_tablename,
       COALESCE(a.flag, 'N') as flag,
       a.tbl_id,
       -- 只需要继承主表的truncate和delete属性;文件推送时，该字段代表输出格式
       COALESCE(a.pre_sql,CASE WHEN t.pre_sql IN('T','D') OR b.db_kind_full='file' THEN t.pre_sql END,'T') as pre_sql,
       a.post_sql,
       COALESCE(a.max_runtime,t.max_runtime,3600) as max_runtime,
       CASE WHEN a.start_time >= t.start_time AND a.start_time BETWEEN x.td AND x.ntd THEN a.start_time END as start_time,
       CASE WHEN a.end_time >= a.start_time AND a.end_time >= t.start_time AND a.end_time BETWEEN x.td AND x.ntd THEN a.end_time END as end_time,
       CASE WHEN a.end_time > a.start_time THEN trunc(EXTRACT(EPOCH FROM (a.end_time - a.start_time))) END as runtime,
       lower(dest_sysid || '_' || substring(task_group from '^[^,]+')) as sou_db_conn,
       lower('v' || a.tbl_id) as dsview,
       CASE WHEN COALESCE(a.flag,'N')='X' OR COALESCE(t.flag,'N')='X' THEN 0 ELSE 1 END as bvalid,
       -- 基表的真实时间字段
       a.start_time as start_time_real, a.end_time as end_time_real
FROM tb_imp_ds2 t
INNER JOIN vw_imp_system b ON b.sysid = t.dest_sysid
INNER JOIN vw_imp_tradetime x ON 1=1
LEFT JOIN tb_imp_ds2_tbls a ON t.ds_id = a.ds_id;

-- Note: Due to PostgreSQL's different hierarchical query syntax,
-- the CONNECT BY clause in vw_imp_ds2_needs would need to be rewritten using recursive CTEs
CREATE OR REPLACE VIEW vw_imp_ds2_needs AS
WITH RECURSIVE t_ds AS (
    SELECT ds_id, task_group, flag, start_time, end_time,
           CASE WHEN task_group ~ '^[A-Z]+' AND length(task_group)>2 THEN 1 ELSE 0 END as bmulti
    FROM vw_imp_ds2 WHERE bvalid=1
),
t_grp_base AS (
    SELECT ds_id, task_group, flag, start_time, end_time, bmulti, task_group as needs, 1 as level
    FROM t_ds WHERE bmulti=0
    UNION ALL
    SELECT ds_id, task_group, flag, start_time, end_time, bmulti,
           substring(task_group from '([A-Z0-9]{2}|[a-z0-9]+$)') as needs, 1 as level
    FROM t_ds WHERE bmulti=1
),
t_grp AS (
    SELECT DISTINCT ds_id, task_group, flag, start_time, end_time, bmulti, needs
    FROM t_grp_base
    WHERE needs IS NOT NULL
)
SELECT t.ds_id, t.task_group, t.flag, t.start_time, t.end_time, t.bmulti, t.needs,
       CASE WHEN a.task_group IS NOT NULL THEN 1 ELSE 0 END as bover
FROM t_grp t
LEFT JOIN vw_imp_taskgroup_over a ON a.task_group=t.needs;

CREATE OR REPLACE VIEW vw_imp_etl AS
SELECT 'WF_ODS'||sou_sysid||'_ETL' as wkf,
       a.sysid,
       a.sys_name,
       a.sysid||'_'||a.sys_name as sysname,
       COALESCE(a.db_start,a.start_kind) as db_start,
       a.db_start_dt,
       'DB_'||sou_sysid as sou_db_conn,
       a.db_kind_full as sou_db_kind,
       a.db_constr as sou_db_constr,
       a.db_user as sou_db_user,
       a.db_pass as sou_db_pass,
       a.conf as sou_db_conf,
       COALESCE(CASE WHEN t.etl_kind='R' THEN t.realtime_sou_owner END, t.sou_owner) as sou_owner,
       CASE WHEN t.sou_tablename LIKE '%${%' THEN fn_imp_param_replace(t.sou_tablename,t.param_sou)::text
            ELSE t.sou_tablename END as sou_tablename,
       COALESCE(CASE WHEN t.etl_kind='R' THEN t.realtime_sou_filter END, t.sou_filter) as sou_filter,
       t.sou_split,
       'ODS'||sou_sysid as dest_owner,
       t.dest_tablename,
       t.dest_part_kind,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.flag
            ELSE CASE WHEN t.flag = 'X' THEN 'X' ELSE 'N' END
       END as flag,
       t.param_sou,
       t.bupdate,
       t.bcreate,
       t.etl_kind,
       t.retry_cnt,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.start_time END as start_time,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd AND t.end_time>=t.start_time THEN t.end_time END as end_time,
       t.runtime,
       t.runtime_add,
       t.tid,
       t.ctid as rid,
       'hadoop_' || 'ODS'||sou_sysid || '_' || t.dest_tablename as spname,
       'ODS' || sou_sysid || '.' || t.dest_tablename as dest,
       t.bpreview,
       t.btdh,
       CASE WHEN t.realtime_interval > 0 OR t.realtime_fixed IS NOT NULL THEN 1 ELSE 0 END as brealtime,
       t.realtime_interval,
       t.realtime_interval_range,
       t.realtime_taskgroup,
       t.realtime_fixed,
       CASE WHEN t.after_retry_pntype IS NOT NULL AND t.after_retry_fixed IS NOT NULL THEN 1 ELSE 0 END as bafter_retry,
       t.after_retry_fixed,
       t.after_retry_pntype,
       CASE WHEN a.bvalid = 1 AND (flag='N' OR (retry_cnt > 0 AND flag = 'E')) THEN 1 ELSE 0 END as brun,
       CASE WHEN flag = 'X' OR a.bvalid = 0 THEN 0 ELSE 1 END as bvalid,
       1 as bcj,
       a.db_kind||'2H' as jobkind
FROM tb_imp_etl t
INNER JOIN vw_imp_tradetime x ON 1 = 1
INNER JOIN vw_imp_system a ON a.sysid = t.sou_sysid AND a.sys_kind = 'etl';

CREATE OR REPLACE VIEW vw_imp_etl_cols AS
SELECT t.tid, x.jobkind, a.data_type, a.column_name_orig as column_name, -- 必须用原始字段名
       CASE WHEN c.entry_value IS NOT NULL OR upper(a.column_name_orig)<>a.column_name OR a.column_name_orig ~ '^[0-9]+$'
            THEN 1 ELSE 0 END as bquota, -- 字段名修改了或者特殊字符，JSON中需要加引号
       lower(t.col_name) as col_name, lower(t.col_type) as col_type, t.col_idx, x.sysid as dbid
FROM tb_imp_tbl_hdp t
INNER JOIN vw_imp_etl x ON x.tid=t.tid
LEFT JOIN tb_imp_tbl_sou a ON a.tid=t.tid AND a.column_name=t.col_name
LEFT JOIN tb_dictionary c ON c.entry_code='2014' AND c.entry_value=a.column_name -- 数据库关键字
WHERE t.col_idx<1000;

CREATE OR REPLACE VIEW vw_imp_etl_coltype AS
SELECT entry_value as coltype, entry_content as hive_type, remark
FROM tb_dictionary t
WHERE entry_code='2011';

CREATE OR REPLACE VIEW vw_imp_etl_judge AS
SELECT t.sysid,
       t.sys_name,
       t.db_name,
       fn_imp_param_replace(t.db_judge_sql)::text as judge_sql,
       fn_imp_param_replace(t.db_judge_pre)::text as judge_pre,
       COALESCE(c.fval,-1) as bstart,
       a.fval,
       a.dw_clt_date as judge_time,
       row_number() OVER(PARTITION BY t.sysid ORDER BY a.dw_clt_date DESC) as px,
       x.param_value,
       t.db_conn
FROM vw_imp_system t
INNER JOIN vw_imp_param x ON x.param_sou='C' AND x.param_kind_0='TD'
LEFT JOIN tb_imp_flag a ON a.kind='ETL_JUDGE' AND a.fid=t.sysid AND a.tradedate=x.param_value::numeric
LEFT JOIN (SELECT fid, fval, row_number() OVER(PARTITION BY fid ORDER BY dw_clt_date DESC) as px
           FROM vw_imp_flag WHERE kind='ETL_START') c ON c.fid=t.sysid AND c.px=1
WHERE t.sys_kind = 'etl' AND t.db_judge_sql IS NOT NULL;

CREATE OR REPLACE VIEW vw_imp_etl_overprec AS
WITH t_flag AS (
    SELECT fid,
           max(CASE WHEN param_kind_0='LTD' AND fval=3 THEN dw_clt_date END) as start_time_ltd,
           max(CASE WHEN param_kind_0='LTD' AND fval=4 THEN dw_clt_date END) as end_time_ltd,
           max(CASE WHEN param_kind_0='TD' AND fval=3 THEN dw_clt_date END) as start_time_td,
           max(CASE WHEN param_kind_0='TD' AND fval=4 THEN dw_clt_date END) as end_time_td
    FROM tb_imp_flag t
    INNER JOIN (SELECT param_kind_0, param_value FROM vw_imp_param WHERE param_sou='C' AND param_kind_0 IN('LTD','TD')) a
       ON a.param_value::numeric = t.tradedate
    WHERE kind IN('ETL_START','ETL_END') AND fval IN(3,4)
    GROUP BY fid
)
SELECT t.sysname, t.db_start, t.db_start_dt, t.total_cnt, t.over_cnt, t.over_prec, t.run_cnt, t.err_cnt, t.no_cnt, t.wait_cnt,
       t.start_time_ltd, t.end_time_ltd, t.start_time_td, t.end_time_td, t.start_time_r, t.end_time_r,
       EXTRACT(EPOCH FROM (end_time_ltd-start_time_ltd))::integer as runtime_ltd,
       EXTRACT(EPOCH FROM (end_time_td-start_time_td))::integer as runtime_td,
       EXTRACT(EPOCH FROM (end_time_r-start_time_r))::integer as runtime_r
FROM (
    SELECT sysname, db_start, db_start_dt,
           count(1) as total_cnt,
           sum(CASE WHEN flag='Y' THEN 1 ELSE 0 END) as over_cnt,
           sum(CASE WHEN flag='Y' THEN 1 ELSE 0 END)::numeric/count(1) as over_prec,
           sum(CASE WHEN flag='R' THEN 1 ELSE 0 END) as run_cnt,
           sum(CASE WHEN flag='E' THEN 1 ELSE 0 END) as err_cnt,
           sum(CASE WHEN flag='N' THEN 1 ELSE 0 END) as no_cnt,
           sum(CASE WHEN flag='W' THEN 1 ELSE 0 END) as wait_cnt,
           min(a.start_time_ltd) as start_time_ltd,
           max(a.end_time_ltd) as end_time_ltd,
           min(a.start_time_td) as start_time_td,
           max(a.end_time_td) as end_time_td,
           min(CASE WHEN start_time>end_time_td THEN start_time END) as start_time_r,
           max(CASE WHEN end_time>end_time_td AND flag='Y' THEN end_time END) as end_time_r
    FROM vw_imp_etl t
    LEFT JOIN t_flag a ON a.fid=t.sysid
    WHERE bvalid = 1
    GROUP BY sysid, sysname, db_start, db_start_dt
) t
ORDER BY t.db_start_dt, t.sysname;

-- Note: This view contains complex Oracle-specific functions and string concatenations
-- that would need custom PostgreSQL functions to fully replicate
CREATE OR REPLACE VIEW vw_imp_etl_soutab AS
WITH t_sql AS ( -- 获取（采集源库，数据服务目标库）的表结构信息
    SELECT kind, sou_db_conn,
           'delete from tb_imp_etl_soutab where sou_db_conn=''' || sou_db_conn || '''' ||
           CASE WHEN count(1)<=30 THEN ' and upper(owner||''.''||table_name) in(' || string_agg('''' || upper(sou_owner||'.'||sou_tablename) || '''', ',') || ')'
                ELSE '' END as pre_sql,
           'select ' || CASE WHEN substr(max(flag),1,1)='1' THEN 'distinct ' ELSE '' END || '''' || sou_db_conn || ''' sou_db_conn,' ||
           CASE WHEN sou_db_kind = 'oracle' THEN
               't.owner,t.table_name,t.column_name,data_type,data_length,data_precision,data_scale,column_id,a.table_type,a.comments tab_comment,b.comments col_comment ' ||
               'from all_tab_cols t ' ||
               'left join all_tab_comments a on a.owner=t.owner and a.table_name=t.table_name ' ||
               'left join all_col_comments b on b.owner=t.owner and b.table_name=t.table_name and b.column_name=t.column_name ' ||
               'where column_id is not null and t.owner||''.''||t.table_name in('
           WHEN sou_db_kind IN('sqlserver','mysql','postgresql') THEN
               CASE WHEN substr(max(flag),1,1)='1' THEN 'TABLE_CATALOG' ELSE 'table_schema' END ||
               ' as owner,TABLE_NAME table_name,COLUMN_NAME,upper(DATA_TYPE) data_type,' ||
               'CHARACTER_MAXIMUM_LENGTH data_length,NUMERIC_PRECISION data_precision,NUMERIC_SCALE data_scale,' ||
               'ORDINAL_POSITION column_id,'''' table_type,'''' tab_comment,'''' col_comment from INFORMATION_SCHEMA.COLUMNS where upper(table_schema||''.''||table_name) in('
           ELSE 'database as owner,table as table_name,name as column_name,type as data_type from system.columns where upper(database||''.''||table) in('
           END || string_agg('''' || upper(sou_owner||'.'||sou_tablename) || '''', ',') || ')' as colsql
    FROM (
        SELECT 'etl' as kind, sou_db_conn, sou_db_kind, sou_owner, sou_tablename,
               CASE WHEN sou_db_kind='sqlserver' AND sou_db_conf LIKE '%[soutab_owner:table_catalog]%' THEN '1' END as flag
        FROM vw_imp_etl
        WHERE sysid<>'ZD'
          AND (bupdate='Y'
              OR sysid IN (SELECT sou_sysid FROM tb_imp_etl GROUP BY sou_sysid HAVING sum(CASE WHEN bupdate='Y' THEN 1 ELSE 0 END)>30)
              OR (SELECT count(1) FROM tb_imp_etl WHERE bupdate='Y')=0)
        UNION ALL
        SELECT 'ds', sou_db_conn, db_kind_full, dest_owner, dest_tablename, NULL
        FROM vw_imp_ds2_mid
        WHERE db_kind_full<>'file'
    ) t
    GROUP BY kind, sou_db_kind, sou_db_conn
)
-- Note: The complex string replacement and dynamic SQL generation would need
-- to be implemented with PostgreSQL-specific functions
SELECT t.kind, t.sou_db_conn, 'PostgreSQL migration needed for complex string operations' as col_json
FROM t_sql t
INNER JOIN vw_imp_system b ON b.db_name=t.sou_db_conn OR upper(b.sysid)=regexp_replace(upper(t.sou_db_conn),'_[0-9A-Z]+$','')
WHERE b.bvalid=1;

CREATE OR REPLACE VIEW vw_imp_flag AS
SELECT t.tradedate, t.kind, t.fid, t.fval, t.dw_clt_date
FROM tb_imp_flag t
INNER JOIN vw_imp_param x
   ON x.param_sou = 'C'
  AND x.param_kind_0 = 'TD'
  AND x.param_value::numeric = t.tradedate;

-- Complete the vw_imp_jobfile view
CREATE OR REPLACE VIEW vw_imp_jobfile AS
SELECT entry_value as jobkind,
       replace(replace(entry_content,
                       '${r' || substr(entry_value, 1, 1) || '}',
                       (SELECT entry_content
                        FROM tb_dictionary a
                        WHERE a.entry_code = '5001'
                          AND a.entry_value = 'r' || substr(t.entry_value, 1, 1))),
               '${w' || substr(entry_value, -1) || '}',
               (SELECT entry_content
                FROM tb_dictionary a
                WHERE a.entry_code = '5001'
                  AND a.entry_value = 'w' || substr(t.entry_value, -1))) as jobfile
FROM tb_dictionary t
WHERE entry_code = '5000';

CREATE OR REPLACE VIEW vw_imp_jy_needbak AS
SELECT lower(dest_tablename) as tbl 
FROM tb_imp_etl t
INNER JOIN tb_imp_sp_com a ON a.sp_id=t.tid AND a.com_idx=120 AND a.com_kind='presto' AND a.flag!='X'
WHERE t.sou_sysid='JY' AND t.flag!='X';

CREATE OR REPLACE VIEW vw_imp_param AS
SELECT param_sou,
       param_kind_0,
       param_kind,
       param_name,
       param_remark,
       param_value
FROM tb_imp_param0
WHERE param_kind_0 IS NOT NULL
UNION ALL
SELECT a.param_sou,
       entry_value as param_kind_0,
       '${' || entry_value || '}' as param_kind,
       '$$' || entry_value as param_name,
       '自定义参数:' || remark as param_remark,
       COALESCE(entry_content, a.param_value) as param_value
FROM tb_dictionary t
INNER JOIN (SELECT param_sou, param_value
            FROM tb_imp_param0
            WHERE param_kind_0 = 'TD') a ON 1 = 1
LEFT JOIN tb_imp_param0 b ON b.param_kind_0 = t.entry_value
WHERE entry_code = '2005'
  AND b.param_kind_0 IS NULL
UNION ALL
SELECT param_sou,
       'T1' as param_kind_0,
       '${T1}' as param_kind,
       '$$T_1' as param_name,
       'T-1日,不与切日挂钩' as param_remark,
       CASE WHEN td=to_char(current_date,'YYYYMMDD') THEN ltd ELSE td END as param_value
FROM (
    SELECT param_sou,
           max(CASE WHEN param_kind_0='TD' THEN param_value END) as td,
           max(CASE WHEN param_kind_0='LTD' THEN param_value END) as ltd
    FROM tb_imp_param0 t
    WHERE param_kind_0 IS NOT NULL AND param_kind_0 IN('TD','LTD')
    GROUP BY param_sou
) t;

-- Note: PostgreSQL doesn't have PIVOT syntax like Oracle, so this view needs a different approach
CREATE OR REPLACE VIEW vw_imp_param_all AS
SELECT 
    max(CASE WHEN param_kind_0='CD' THEN param_value END) as CD,
    max(CASE WHEN param_kind_0='CM0' THEN param_value END) as CM0,
    max(CASE WHEN param_kind_0='CM1' THEN param_value END) as CM1,
    max(CASE WHEN param_kind_0='CQ0' THEN param_value END) as CQ0,
    max(CASE WHEN param_kind_0='CQ1' THEN param_value END) as CQ1,
    max(CASE WHEN param_kind_0='CW0' THEN param_value END) as CW0,
    max(CASE WHEN param_kind_0='CW1' THEN param_value END) as CW1,
    max(CASE WHEN param_kind_0='CWM' THEN param_value END) as CWM,
    max(CASE WHEN param_kind_0='CWS' THEN param_value END) as CWS,
    max(CASE WHEN param_kind_0='CY0' THEN param_value END) as CY0,
    max(CASE WHEN param_kind_0='CY1' THEN param_value END) as CY1,
    max(CASE WHEN param_kind_0='L10TD' THEN param_value END) as L10TD,
    max(CASE WHEN param_kind_0='L180TD' THEN param_value END) as L180TD,
    max(CASE WHEN param_kind_0='L20TD' THEN param_value END) as L20TD,
    max(CASE WHEN param_kind_0='L2M0' THEN param_value END) as L2M0,
    max(CASE WHEN param_kind_0='L2M1' THEN param_value END) as L2M1,
    max(CASE WHEN param_kind_0='L2Q0' THEN param_value END) as L2Q0,
    max(CASE WHEN param_kind_0='L2Q1' THEN param_value END) as L2Q1,
    max(CASE WHEN param_kind_0='L2TM' THEN param_value END) as L2TM,
    max(CASE WHEN param_kind_0='L2TY' THEN param_value END) as L2TY,
    max(CASE WHEN param_kind_0='L2Y0' THEN param_value END) as L2Y0,
    max(CASE WHEN param_kind_0='L2Y1' THEN param_value END) as L2Y1,
    max(CASE WHEN param_kind_0='L30TD' THEN param_value END) as L30TD,
    max(CASE WHEN param_kind_0='L40TD' THEN param_value END) as L40TD,
    max(CASE WHEN param_kind_0='L5TD' THEN param_value END) as L5TD,
    max(CASE WHEN param_kind_0='L5TDM' THEN param_value END) as L5TDM,
    max(CASE WHEN param_kind_0='L60TD' THEN param_value END) as L60TD,
    max(CASE WHEN param_kind_0='L90TD' THEN param_value END) as L90TD,
    max(CASE WHEN param_kind_0='LD180' THEN param_value END) as LD180,
    max(CASE WHEN param_kind_0='LD180T' THEN param_value END) as LD180T,
    max(CASE WHEN param_kind_0='LD30' THEN param_value END) as LD30,
    max(CASE WHEN param_kind_0='LD30T' THEN param_value END) as LD30T,
    max(CASE WHEN param_kind_0='LD365' THEN param_value END) as LD365,
    max(CASE WHEN param_kind_0='LD365T' THEN param_value END) as LD365T,
    max(CASE WHEN param_kind_0='LD730' THEN param_value END) as LD730,
    max(CASE WHEN param_kind_0='LD730T' THEN param_value END) as LD730T,
    max(CASE WHEN param_kind_0='LD800T' THEN param_value END) as LD800T,
    max(CASE WHEN param_kind_0='LD90' THEN param_value END) as LD90,
    max(CASE WHEN param_kind_0='LD90T' THEN param_value END) as LD90T,
    max(CASE WHEN param_kind_0='LM0' THEN param_value END) as LM0,
    max(CASE WHEN param_kind_0='LM1' THEN param_value END) as LM1,
    max(CASE WHEN param_kind_0='LQ0' THEN param_value END) as LQ0,
    max(CASE WHEN param_kind_0='LQ1' THEN param_value END) as LQ1,
    max(CASE WHEN param_kind_0='LTD' THEN param_value END) as LTD,
    max(CASE WHEN param_kind_0='LTM' THEN param_value END) as LTM,
    max(CASE WHEN param_kind_0='LTQ' THEN param_value END) as LTQ,
    max(CASE WHEN param_kind_0='LTY' THEN param_value END) as LTY,
    max(CASE WHEN param_kind_0='LW0' THEN param_value END) as LW0,
    max(CASE WHEN param_kind_0='LW1' THEN param_value END) as LW1,
    max(CASE WHEN param_kind_0='LY0' THEN param_value END) as LY0,
    max(CASE WHEN param_kind_0='LY1' THEN param_value END) as LY1,
    max(CASE WHEN param_kind_0='LYTD' THEN param_value END) as LYTD,
    max(CASE WHEN param_kind_0='NTD' THEN param_value END) as NTD,
    max(CASE WHEN param_kind_0='TD' THEN param_value END) as TD,
    max(CASE WHEN param_kind_0='TF' THEN param_value END) as TF,
    max(CASE WHEN param_kind_0='TM' THEN param_value END) as TM,
    max(CASE WHEN param_kind_0='TQ' THEN param_value END) as TQ,
    max(CASE WHEN param_kind_0='TY' THEN param_value END) as TY
FROM tb_imp_param0 
WHERE param_sou='C';

CREATE OR REPLACE VIEW vw_imp_param_file AS
SELECT CASE param_sou 
           WHEN 'C' THEN 'param_sys'
           WHEN 'N' THEN 'param_sys_next'
           WHEN 'L' THEN 'param_sys_yest'
           WHEN 'T' THEN 'param_sys_test'
       END as param_file,
       replace('[Global],' || string_agg(param_name || '=' || param_value, ','), ',', chr(10)) as param_content
FROM vw_imp_param
GROUP BY param_sou;

CREATE OR REPLACE VIEW vw_imp_plan AS
SELECT pn_id,
       pn_type,
       fn_imp_pnname(pn_type) as pn_type_name,
       pn_fixed,
       b.dt_full,
       pn_interval,
       pn_range,
       fn_imp_pnname(pn_type,pn_fixed,pn_interval,pn_range) as spname,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.flag ELSE 'N' END as flag,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.start_time END as start_time,
       CASE WHEN t.end_time BETWEEN x.td AND x.ntd THEN t.end_time END as end_time,
       runtime,
       CASE WHEN flag<>'R' AND pn_type<>'9'
                 AND (SELECT count(1) FROM tb_imp_sp_com WHERE sp_id = t.pn_id AND COALESCE(flag,'N')<>'X')>0
                 AND fn_imp_timechk(current_timestamp, pn_fixed, pn_interval, pn_range, COALESCE(bexit,'Y'))=1
            THEN 1 ELSE 0 END as brun,
       fn_imp_pntype(pn_type) as bpntype
FROM tb_imp_plan t
INNER JOIN vw_imp_tradetime x ON 1=1
LEFT JOIN vw_imp_date b ON b.dt=substring(t.pn_fixed from '\d+')::numeric;

-- Note: This view uses PIVOT which doesn't exist in PostgreSQL, needs manual conversion
CREATE OR REPLACE VIEW vw_imp_plan_all AS
WITH t_pn AS (
    SELECT t.pn_id, t.pn_type_name, t.pn_type, t.dt_full, t.spname, t.flag as pn_flag, 
           t.start_time as pn_start, t.end_time as pn_end,
           substring(a.com_text from '[^-#' || chr(10) || ']+') || a.flag as com_name,
           row_number() OVER(PARTITION BY t.pn_id ORDER BY a.com_idx) as px
    FROM vw_imp_plan t
    INNER JOIN tb_imp_sp_com a ON a.sp_id=t.pn_id
)
SELECT pn_type, dt_full, pn_type_name, spname, pn_flag,
       substr(max(CASE WHEN px=1 THEN com_name END), 1, length(max(CASE WHEN px=1 THEN com_name END))-1) as c1,
       substr(max(CASE WHEN px=1 THEN com_name END), -1) as c1f,
       substr(max(CASE WHEN px=2 THEN com_name END), 1, length(max(CASE WHEN px=2 THEN com_name END))-1) as c2,
       substr(max(CASE WHEN px=2 THEN com_name END), -1) as c2f,
       substr(max(CASE WHEN px=3 THEN com_name END), 1, length(max(CASE WHEN px=3 THEN com_name END))-1) as c3,
       substr(max(CASE WHEN px=3 THEN com_name END), -1) as c3f,
       substr(max(CASE WHEN px=4 THEN com_name END), 1, length(max(CASE WHEN px=4 THEN com_name END))-1) as c4,
       substr(max(CASE WHEN px=4 THEN com_name END), -1) as c4f,
       substr(max(CASE WHEN px=5 THEN com_name END), 1, length(max(CASE WHEN px=5 THEN com_name END))-1) as c5,
       substr(max(CASE WHEN px=5 THEN com_name END), -1) as c5f,
       substr(max(CASE WHEN px=6 THEN com_name END), 1, length(max(CASE WHEN px=6 THEN com_name END))-1) as c6,
       substr(max(CASE WHEN px=6 THEN com_name END), -1) as c6f
FROM t_pn
GROUP BY pn_id, pn_type, dt_full, pn_type_name, spname, pn_flag
UNION ALL
SELECT pn_type, 
       (SELECT dt_full FROM vw_imp_date WHERE dt=substring(pn_fixed from '\d+')::numeric) as dt_full,
       fn_imp_pnname(pn_type) as pn_type_name,
       fn_imp_pnname(pn_type,pn_fixed,pn_interval,pn_range) as spname,
       flag as pn_flag,
       '数据服务:' || fn_imp_value('taskname',ds_id)::text as c1, flag as c1f,
       NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM tb_imp_ds2
WHERE pn_type IS NOT NULL AND flag<>'X'
UNION ALL
SELECT pn_type,
       (SELECT dt_full FROM vw_imp_date WHERE dt=substring(pn_fixed from '\d+')::numeric) as dt_full,
       fn_imp_pnname(pn_type) as pn_type_name,
       fn_imp_pnname(pn_type,pn_fixed,pn_interval,pn_range) as spname,
       flag as pn_flag,
       'SP计算:' || fn_imp_value('taskname',sp_id)::text as c1, flag as c1f,
       NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM tb_imp_sp
WHERE pn_type IS NOT NULL AND flag<>'X'
ORDER BY 1,2,4;

-- Note: PostgreSQL doesn't support Oracle's CONNECT BY, using recursive CTE instead
CREATE OR REPLACE VIEW vw_imp_realtimes AS
WITH t_interval AS ( -- 根据轮询时间，计算出全天的具体执行时间点(不涉及具体的表，只计算时间)
    SELECT realtime_interval, dt_full as times
    FROM (SELECT realtime_interval
          FROM tb_imp_etl
          WHERE COALESCE(realtime_interval, 0) > 0
          GROUP BY realtime_interval) t
    INNER JOIN vw_imp_date a
       ON (substr(a.dt_full, 1, 2)::integer * 60 + substr(a.dt_full, 3, 2)::integer) % t.realtime_interval = 0 
      AND a.dt_full BETWEEN '0800' AND '1800'
),
t_fixed_base AS (
    SELECT string_agg(DISTINCT realtime_fixed, ',') as fixed
    FROM tb_imp_etl
    WHERE realtime_fixed IS NOT NULL
),
t_fixed AS ( -- 计算所有的定点时间(不涉及具体的表，只计算时间)
    SELECT DISTINCT trim(unnest(string_to_array(fixed, ','))) as fixed
    FROM t_fixed_base
)
SELECT t.tid, a.times
FROM tb_imp_etl t
INNER JOIN t_interval a
   ON a.realtime_interval = t.realtime_interval
  AND a.times BETWEEN 
      (SELECT dt_full FROM vw_imp_date WHERE dt = COALESCE(substring(t.realtime_interval_range from '^\d+')::numeric, 8))
      AND
      (SELECT dt_full FROM vw_imp_date WHERE dt = COALESCE(substring(t.realtime_interval_range from '\d+$')::numeric, 18))
WHERE COALESCE(t.realtime_interval, 0) > 0
UNION ALL
SELECT t.tid, b.dt_full
FROM tb_imp_etl t
INNER JOIN t_fixed a
   ON (',' || t.realtime_fixed || ',') LIKE ('%,' || a.fixed || ',%')
INNER JOIN vw_imp_date b
   ON regexp_replace(b.dt_full, '(^0+|00$)', '', 'g') = a.fixed
WHERE t.realtime_fixed IS NOT NULL;

CREATE OR REPLACE VIEW vw_imp_realtimes_etl AS
SELECT max(CASE WHEN a.times<=to_char(current_timestamp,'HH24MI') THEN times END) as last_times,
       min(CASE WHEN a.times>to_char(current_timestamp,'HH24MI') THEN times END) as next_times,
       t.dest as spname, t.start_time, t.end_time, t.flag
FROM vw_imp_etl t
INNER JOIN vw_imp_realtimes a ON a.tid=t.tid 
  AND a.times BETWEEN to_char(current_timestamp - interval '30 minutes','HH24MI') 
                  AND to_char(current_timestamp + interval '30 minutes','HH24MI')
WHERE fn_imp_pntype('2')=1 -- 交易日当天
GROUP BY t.dest, t.start_time, t.end_time, t.flag
ORDER BY 2, 1 DESC, 3;

CREATE OR REPLACE VIEW vw_imp_sp AS
SELECT lower(t.sp_owner) as sp_owner,
       lower(t.sp_name) as sp_name,
       t.sp_id,
       t.ctid as rid,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.flag
            ELSE CASE WHEN t.flag = 'X' THEN 'X' ELSE 'N' END
       END as flag,
       t.run_freq,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd THEN t.start_time END as start_time,
       CASE WHEN t.start_time BETWEEN x.td AND x.ntd AND t.end_time>=t.start_time THEN t.end_time END as end_time,
       t.retry_cnt,
       t.runtime,
       t.realtime_taskgroup,
       a.need_sou,
       a.need_sp,
       a.sp_alltabs,
       a.sp_dest,
       a.through_need_sou,
       a.through_need_sp,
       t.task_group,
       t.param_sou,
       t.remark,
       lower(sp_owner || '_' || sp_name) as spname,
       CASE WHEN flag <> 'X' THEN 1 ELSE 0 END as bvalid,
       CASE WHEN flag='N' OR (retry_cnt > 0 AND flag = 'E') THEN 1 ELSE 0 END as brun,
       fn_imp_freqchk(run_freq) as bfreq,
       CASE WHEN pn_type IS NOT NULL AND (pn_interval>0 OR pn_fixed IS NOT NULL) THEN 1 ELSE 0 END as bplan
FROM tb_imp_sp t
INNER JOIN vw_imp_tradetime x ON 1 = 1
LEFT JOIN tb_imp_sp_needall a ON a.sp_id = t.sp_id;

CREATE OR REPLACE VIEW vw_imp_sp_dest_equal AS
WITH t_dest AS (
    SELECT sp_id, table_name
    FROM tb_imp_sp_needtab t
    WHERE kind = 'DEST'
      AND table_name NOT LIKE 'XXX%'
      AND sp_id NOT IN (SELECT sp_id FROM vw_imp_sp WHERE bvalid = 0)
    UNION ALL
    SELECT sp_id, upper(sp_owner || '.' || sp_name) as table_name
    FROM vw_imp_sp
    WHERE bvalid = 1
)
SELECT t.sp_id, t.table_name as dest
FROM t_dest t
INNER JOIN (SELECT table_name
            FROM t_dest t
            INNER JOIN (SELECT upper(db_name||'.'||tbl_name) as tbls FROM tb_imp_etl_tbls GROUP BY db_name,tbl_name) a 
               ON a.tbls=t.table_name
            GROUP BY table_name
            HAVING count(1) > 1) a ON a.table_name = t.table_name;

CREATE OR REPLACE VIEW vw_imp_sp_needs AS
WITH t_need_run AS (
    -- SP执行情况
    SELECT sp_id, flag, end_time
    FROM vw_imp_sp
    WHERE bvalid = 1 AND bfreq = 1
    UNION ALL
    -- 数据源采集情况
    SELECT sysid,
           CASE WHEN a.fid IS NOT NULL THEN 'Y' ELSE 'N' END as flag,
           dw_clt_date as end_time
    FROM vw_imp_system t
    LEFT JOIN vw_imp_flag a
      ON a.kind = 'ETL_END'
     AND a.fval = 4
     AND a.fid = t.sysid
    WHERE t.sys_kind = 'etl'
)
SELECT t.sp_id,
       t.flag,
       t.start_time,
       b.sp_id as needs,
       b.flag as needs_flag,
       b.end_time as needs_end_time
FROM vw_imp_sp t
INNER JOIN tb_imp_sp_needtab a
   ON a.sp_id = t.sp_id
  AND a.kind = 'NEEDS'
INNER JOIN t_need_run b
   ON b.sp_id = a.table_name
WHERE t.bvalid = 1
  AND t.bfreq = 1
  AND t.bplan = 0;

CREATE OR REPLACE VIEW vw_imp_sp_source AS
WITH t_sp AS (
    SELECT b.sp_id as parent_id,
           b.sp_owner || '.' || b.sp_name as parent_name,
           COALESCE(a.sp_id, gen_random_uuid()::text) as cate_id,
           CASE WHEN a.sp_id IS NOT NULL THEN a.sp_owner || '.' || a.sp_name
                ELSE t.table_name
           END as cate_name
    FROM (SELECT sp_id, lower(table_name) as table_name
          FROM tb_imp_sp_needtab
          WHERE kind = 'ALL'
          EXCEPT
          SELECT sp_id, lower(table_name) as table_name
          FROM tb_imp_sp_needtab
          WHERE kind = 'DEST') t
    LEFT JOIN tb_imp_sp a ON a.sp_owner || '.' || a.sp_name = t.table_name
    LEFT JOIN tb_imp_sp b ON b.sp_id = t.sp_id
)
SELECT parent_id, cate_id, cate_name, 1 as cate_value FROM t_sp
UNION ALL
SELECT DISTINCT NULL, parent_id, parent_name, 1 FROM t_sp;

CREATE OR REPLACE VIEW vw_imp_system AS
WITH t_dt AS ( -- 根据配置的db_start,计算出具体的时间,多个时间的只算第一个
    SELECT a.db_start, t.dt_full
    FROM vw_imp_date t
    INNER JOIN (SELECT db_start,
                       substring(db_start from '^\d+') as db_start_dt
                FROM tb_imp_db WHERE db_start IS NOT NULL
                GROUP BY db_start) a ON a.db_start_dt::numeric = t.dt
)
SELECT 'etl' as sys_kind,
       db_id_etl as sysid,
       sys_name,
       'DB_'||db_id_etl as db_name,
       t.db_constr,
       netchk,
       CASE WHEN t.bvalid = 'Y' THEN 1 ELSE 0 END as bvalid,
       db_user_etl as db_user,
       db_pass_etl as db_pass,
       COALESCE(db_paral_etl,10) as db_paral,
       db_kind,
       db_kind_full,
       conf,
       CASE WHEN t.db_judge_sql IS NOT NULL THEN '标志' ELSE '定时' END as start_kind,
       t.db_start_type,
       t.db_start,
       a.dt_full as db_start_dt,
       lower('create database if not exists ods' || t.db_id_etl ||
             ' location ''/ods/ods' || t.db_id_etl || ''';') as create_db,
       t.db_judge_sql,
       t.db_judge_pre,
       '-U "' || db_constr || '" -u "' || db_user_etl || '" -p "' || db_pass_etl || '"' as db_conn,
       CASE db_kind
           WHEN 'M' THEN -- mysql
               'mysql -h' || substring(netchk from '^[^,]+') || ' -P' || substring(netchk from '[^,]+$') || 
               ' -u' || db_user_etl || ' -p''' || db_pass_etl || ''' ' || 
               substring(regexp_replace(db_constr,'\?.*$','','g') from '[0-9a-zA-Z_]+$')
           WHEN 'O' THEN -- oracle
               'sqlplus ' || db_user_etl || '/' || db_pass_etl || '@' || 
               regexp_replace(substring(db_constr from '@.+$'),'@([^:]+):([^:]+)(:|/)([^:]+)','\1:\2/\4','g')
       END as db_conn_cmd
FROM vw_imp_db t
LEFT JOIN t_dt a ON a.db_start=t.db_start
WHERE db_id_etl IS NOT NULL
UNION ALL
SELECT 'ds' as sys_kind,
       db_id_ds as sysid,
       sys_name,
       db_id_ds as db_name,
       db_constr,
       netchk,
       CASE WHEN bvalid = 'Y' THEN 1 ELSE 0 END as bvalid,
       db_user_ds as db_user,
       db_pass_ds as db_pass,
       COALESCE(db_paral_ds,3) as db_paral,
       db_kind,
       db_kind_full,
       conf,
       NULL as start_kind, NULL as db_start_type, NULL as db_start, NULL as db_start_dt, 
       NULL as create_db, NULL as db_judge_sql, NULL as db_judge_pre,
       '-U "' || db_constr || '" -u "' || db_user_ds || '" -p "' || db_pass_ds || '"' as db_conn,
       CASE db_kind
           WHEN 'M' THEN -- mysql
               'mysql -h' || substring(netchk from '^[^,]+') || ' -P' || substring(netchk from '[^,]+$') || 
               ' -u' || db_user_ds || ' -p''' || db_pass_ds || ''' ' || 
               substring(regexp_replace(db_constr,'\?.*$','','g') from '[0-9a-zA-Z_]+$')
           WHEN 'O' THEN -- oracle
               'sqlplus ' || db_user_ds || '/' || db_pass_ds || '@' || 
               regexp_replace(substring(db_constr from '@.+$'),'@([^:]+):([^:]+)(:|/)([^:]+)','\1:\2/\4','g')
       END as db_conn_cmd
FROM vw_imp_db
WHERE db_id_ds IS NOT NULL;

CREATE OR REPLACE VIEW vw_imp_system_allsql AS
SELECT db_kind_full,
       db_constr,
       db_user_etl as db_user,
       db_pass_etl as db_pass,
       lower(db_id_etl) as sysid
FROM vw_imp_db
WHERE db_id_etl IS NOT NULL
  AND netchk IS NOT NULL
UNION ALL
SELECT db_kind_full, db_constr, db_user_ds, db_pass_ds, lower(db_id_ds)
FROM vw_imp_db
WHERE db_id_ds IS NOT NULL
  AND netchk IS NOT NULL;

-- Note: PostgreSQL doesn't support Oracle's CONNECT BY, using string functions instead
CREATE OR REPLACE VIEW vw_imp_taskgroup AS
WITH t_tgs AS ( -- 所有配置的任务组汇总为一个字段，便于下一步拆分
    SELECT string_agg(task_group, ',') as task_group
    FROM (SELECT task_group
          FROM tb_imp_sp
          WHERE task_group IS NOT NULL
          GROUP BY task_group
          UNION ALL
          SELECT realtime_taskgroup
          FROM tb_imp_etl
          WHERE realtime_taskgroup IS NOT NULL
          GROUP BY realtime_taskgroup) t
)
SELECT DISTINCT trim(unnest(string_to_array(task_group, ','))) as task_group
FROM t_tgs
WHERE task_group IS NOT NULL;

CREATE OR REPLACE VIEW vw_imp_taskgroup_detail AS
WITH t_tg_pre AS ( -- 生成任务组的任务
    SELECT t.task_group, a.kind,
           count(1) as allcnt,
           sum(CASE WHEN flag='Y' THEN 1 ELSE 0 END) as ycnt,
           min(CASE WHEN flag='Y' THEN a.start_time END) as start_time,
           max(CASE WHEN flag='Y' THEN a.end_time END) as end_time,
           least(sum(runtime), round(EXTRACT(EPOCH FROM (max(CASE WHEN flag='Y' THEN a.end_time END) - min(CASE WHEN flag='Y' THEN a.start_time END))))) as runtime,
           sum(CASE WHEN flag='R' THEN 1 ELSE 0 END) as rcnt,
           min(CASE WHEN flag='R' THEN a.start_time END) as start_time_r,
           sum(CASE WHEN flag='N' THEN 1 ELSE 0 END) as ncnt,
           sum(CASE WHEN flag='E' THEN 1 ELSE 0 END) as ecnt,
           round(sum(CASE WHEN flag IN('Y') THEN 1 ELSE 0 END)::numeric/count(1), 4) as prec
    FROM vw_imp_taskgroup t
    INNER JOIN (
        SELECT 'SP计算' as kind, task_group, flag, start_time, end_time, runtime
        FROM vw_imp_sp
        WHERE task_group IS NOT NULL AND bvalid=1 AND bfreq=1
        UNION ALL
        SELECT '实时采集', realtime_taskgroup, flag, start_time, end_time, runtime
        FROM vw_imp_etl
        WHERE realtime_taskgroup IS NOT NULL AND bvalid=1
    ) a ON (',' || a.task_group || ',') LIKE ('%,' || t.task_group || ',%')
    GROUP BY t.task_group, a.kind
),
t_tg_post AS ( -- 任务组调起的后续服务
    SELECT '数据服务' as kind2, regexp_replace(task_group,'[A-Z0-9]{2}','','g') as task_group2, 
           ds_name, flag as flag2, start_time as start_time2,
           CASE WHEN flag IN('E','Y') THEN end_time 
                ELSE start_time + (runtime || ' seconds')::interval 
           END as end_time2
    FROM vw_imp_ds2
    WHERE length(task_group)>2
    UNION ALL
    SELECT 'SP计算', realtime_taskgroup, spname, flag, start_time,
           CASE WHEN flag IN('E','Y') THEN end_time 
                ELSE start_time + (runtime || ' seconds')::interval 
           END
    FROM vw_imp_sp
    WHERE bvalid=1 AND realtime_taskgroup IS NOT NULL
)
SELECT t.task_group, t.kind, t.allcnt, t.ycnt, t.start_time, t.end_time, t.runtime, 
       t.rcnt, t.start_time_r, t.ncnt, t.ecnt, t.prec, 
       a.kind2, a.task_group2, a.ds_name, a.flag2, a.start_time2, a.end_time2,
       CASE WHEN b.fid IS NOT NULL THEN '是' ELSE '' END as bflag,
       b.dw_clt_date as flag_time,
       CASE WHEN allcnt<>ycnt AND b.fid IS NOT NULL THEN '未执行完就生成标志'
            WHEN b.fid IS NOT NULL AND start_time2<b.dw_clt_date THEN '没有调起后续服务就完成标志'
            WHEN (b.fid IS NULL OR rcnt>0) AND flag2='R' THEN '服务已经启动，标志基础任务未完成'
            WHEN COALESCE(prec,0)>1 THEN '数据服务执行超时'
            WHEN ecnt>0 THEN '生成任务组的任务报错'
            WHEN flag2='E' THEN '任务组调起的服务报错'
       END as errmsg
FROM t_tg_pre t
LEFT JOIN t_tg_post a ON a.task_group2=t.task_group
LEFT JOIN vw_imp_flag b ON b.kind='TASK_GROUP' AND b.fid=t.task_group;

CREATE OR REPLACE VIEW vw_imp_taskgroup_over AS
SELECT a.task_group
FROM (SELECT task_group, flag
      FROM vw_imp_sp
      WHERE task_group IS NOT NULL
        AND bvalid = 1
        AND bfreq = 1
      UNION
      SELECT realtime_taskgroup, flag
      FROM vw_imp_etl
      WHERE realtime_taskgroup IS NOT NULL
        AND etl_kind = 'R'
        AND bvalid = 1) t
INNER JOIN vw_imp_taskgroup a
   ON (',' || t.task_group || ',') LIKE ('%,' || a.task_group || ',%')
GROUP BY a.task_group
HAVING count(1) = sum(CASE WHEN flag = 'Y' THEN 1 ELSE 0 END)
UNION ALL
-- ODS采集成功
SELECT t.sysid
FROM vw_imp_system t
INNER JOIN (SELECT fid,
                   fval,
                   dw_clt_date,
                   row_number() OVER(PARTITION BY fid ORDER BY dw_clt_date DESC) as px
            FROM vw_imp_flag
            WHERE kind = 'ETL_END') a
   ON a.fid = t.sysid
  AND a.px = 1
WHERE t.sys_kind = 'etl' AND a.fval = 4;

-- Note: Missing vw_imp_tradetime view - this would need to be created based on the original Oracle definition
