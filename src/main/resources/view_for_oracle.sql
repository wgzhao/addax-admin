

CREATE OR REPLACE FORCE VIEW "STG01"."VW_CI_DEPLOY" ("DEP_ID", "PROJ_NAME", "FILE_PATH", "FILE_NAME", "FILE_CONTENT", "FILE_MD5", "UPDT_DATE", "SPNAME", "BVALID") AS
  select t.dep_id,
       t.proj_name,
       t.file_path,
       t.file_name,
       t.file_content,
       t.file_md5,
       t.updt_date,
       regexp_replace(lower(t.file_name), '\.sql$') spname,
       case when regexp_like(lower(t.file_name), '^[a-z0-9_]+\.[a-z0-9_]+\.sql$') then 1 else 0 end bvalid
  from stg01.tb_ci_deploy t
 inner join stg01.vw_ci_deploy_id a
    on a.dep_id = t.dep_id
   and a.next_dep_id is null
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_CI_DEPLOY_ID" ("DEP_ID", "DEP_DATE", "LAST_DEP_ID", "NEXT_DEP_ID") AS
  select dep_id,
       max_date dep_date,
       lag(dep_id, 1) over(partition by proj_name order by max_date) last_dep_id,
       lead(dep_id, 1) over(partition by proj_name order by max_date) next_dep_id
  from (select 'xx' as proj_name, dep_id, max(updt_date) max_date
          from tb_ci_deploy
         group by dep_id);
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_CHECK_SOUTAB" ("SOU_DB_CONN", "OWNER", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "DATA_TYPE_LAST", "HIVE_TYPE", "HIVE_TYPE_LAST", "DATA_LENGTH", "DATA_LENGTH_LAST", "DATA_PRECISION", "DATA_PRECISION_LAST", "DATA_SCALE", "DATA_SCALE_LAST", "DW_CLT_DATE", "DW_CLT_DATE_LAST") AS
  select nvl(t.sou_db_conn, a.sou_db_conn) as sou_db_conn,
       nvl(t.owner, a.owner) as owner,
       nvl(t.table_name, a.table_name) as table_name,
       nvl(t.column_name, a.column_name) as column_name,
       t.data_type,
       a.data_type as data_type_last,
       (select hive_type from vw_imp_etl_coltype where coltype=upper(t.data_type)) hive_type,
       (select hive_type from vw_imp_etl_coltype where coltype=upper(a.data_type)) hive_type_last,
       t.data_length,
       a.data_length as data_length_last,
       t.data_precision,
       a.data_precision as data_precision_last,
       t.data_scale,
       a.data_scale as data_scale_last,
       t.dw_clt_date,
       a.dw_clt_date as dw_clt_date_last
  from stg01.tb_imp_etl_soutab t
  full join backup.v_stg01_tb_imp_etl_soutab a
    on a.sou_db_conn = t.sou_db_conn
   and a.owner = t.owner
   and a.table_name = t.table_name
   and a.column_name = t.column_name
 where nvl(t.column_name, '') <> nvl(a.column_name, '')
    or t.data_type <> a.data_type
    or t.data_length <> a.data_length
    or t.data_precision <> a.data_precision
    or t.data_scale <> a.data_scale
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_CHK_INF" ("ENGINE", "CHK_IDX", "CHK_SENDTYPE", "CHK_MOBILE", "BPNTYPE", "CHK_KIND", "CHK_SQL") AS
  with t_inf as	--前端自定义的检测项
    (select entry_value chk_idx,
            regexp_replace(regexp_substr(entry_content, '\[S[^]]+'), '^(\[S)') chk_sendtype,
            regexp_replace(regexp_substr(entry_content, '\[M[^]]+'), '^(\[M)') chk_mobile,
            nvl(regexp_replace(regexp_substr(entry_content, '\[P[^]]+'), '^(\[P)'),'3') chk_pntype,
            regexp_substr(entry_content, '[^]]+$') chk_kind,
            remark chk_sql,
            case entry_code when '1073' then 'allsql' when '1074' then 'ini' end as engine
       from stg01.tb_dictionary
      where entry_code in('1073','1074')
        and remark is not null
        and entry_content not like '%[x]%'),
  t_tbl_range as   --核心主表：需要检查的表及字段
    (select t.owner,
            t.table_name,
            t.column_name,
            dense_rank() over(partition by owner,table_name order by nvl(substr(a.entry_value, 1, 1), ' ') desc) pk_lev
       from all_tab_cols t
       left join stg01.tb_dictionary a
         on a.entry_code = '1077'
        and a.entry_content = t.column_name
      where t.owner = 'STG01'
        and t.table_name in (select entry_value from stg01.tb_dictionary where entry_code = '1075')
        and t.column_name not in (select entry_value from stg01.tb_dictionary where entry_code = '1076')),
  t_tbl_has2 as  --核心主表：检查字段在备份表中是否存在
    (select decode(owner,'STG01',table_name,
                         'BACKUP',regexp_replace(table_name, '^V_[^_]+_')) table_name,
            column_name
       from all_tab_cols t
      where data_type in ('VARCHAR2', 'NUMBER', 'CHAR')
        and (owner = 'STG01' or (owner = 'BACKUP' and regexp_like(table_name,'^V_STG01_')))
      group by decode(owner,'STG01',table_name,
                            'BACKUP',regexp_replace(table_name, '^V_[^_]+_')),column_name
     having count(1) = 2),
  t_tbl as  --针对配置主表内容变更的额外检测项，在自定义etl004的基础上新增检测项
    (select t.owner,t.table_name,t.column_name,c.column_name pk,nvl(regexp_substr(comments, '^[^,，（:：]+'), t.column_name) comments
    from t_tbl_range t
    inner join t_tbl_has2 a on a.table_name = t.table_name and a.column_name = t.column_name
    left join all_col_comments b on b.owner = t.owner and b.table_name = t.table_name and b.column_name = t.column_name
    left join t_tbl_range c on c.pk_lev=1 and c.owner = t.owner and c.table_name = t.table_name and c.column_name = c.column_name),
  t_all as	--自定义配置项+配置主表内容变更
    (select * from t_inf where chk_idx<>'etl004'
    union all
    select a.chk_idx,a.chk_sendtype,a.chk_mobile,'3',a.chk_kind,
      'select '''||table_name||''' chk_name,to_char(stg01.fn_imp_value(''taskname'',t.'||pk||')||''的'||
          column_name||'['||comments||']'||':''||substr(a.'||column_name||',1,200)||''=>''||substr(t.'||column_name||
          ',1,200)) chk_content from stg01.'||table_name||' t inner join backup.v_stg01_'||table_name||
          ' a on a.'||pk||'=t.'||pk||' where nvl(t.'||column_name||',0)<>nvl(a.'||column_name||',0)','ini'
       from t_tbl t
      inner join t_inf a on a.chk_idx='etl004'
      where t.column_name <> t.pk
      union all
     select a.chk_idx,a.chk_sendtype,a.chk_mobile,'3',a.chk_kind,t.chk_sql,'ini'
     from (
       select 'select '''||table_name||''' chk_name,stg01.fn_imp_value(''taskname'',nvl(t.'||pk||',a.'||pk||')) || case when t.'||pk||' is null then ''今日被删除---'' when a.'||pk||' is null then ''今日新增+++'' end chk_content '||
              'from '||owner||'.'||table_name||' t full join backup.v_'||owner||'_'||table_name||' a on a.'||pk||'=t.'||pk||' where t.'||pk||' is null or a.'||pk||' is null' chk_sql
         from t_tbl
        group by owner,table_name,pk ) t
     inner join t_inf a on a.chk_idx='etl004')
select engine,
       chk_idx,
       chk_sendtype,
       chk_mobile,
       stg01.fn_imp_pntype(chk_pntype) bpntype,
       chk_kind,
       'select ''' || nvl(chk_mobile, '1') || ''' chk_mobile,''' ||
       chk_sendtype || ''' chk_sendtype,''' || chk_kind ||
       ''' chk_kind,chk_name,chk_content from (' || chk_sql || ')' chk_sql
  from t_all;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_DATE" ("DT", "DT_FULL") AS
  select to_number(entry_value) as dt,entry_content as dt_full from stg01.tb_dictionary
where entry_code='1022'
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_DB" ("DB_NAME", "DB_CONSTR", "DB_ID_ETL", "DB_USER_ETL", "DB_PASS_ETL", "DB_PARAL_ETL", "DB_ID_DS", "DB_USER_DS", "DB_PASS_DS", "DB_PARAL_DS", "DB_START", "DB_START_TYPE", "DB_JUDGE_SQL", "DB_JUDGE_PRE", "DB_REMARK", "DID", "BVALID", "SYS_NAME", "NETCHK", "DB_KIND", "DB_KIND_FULL", "CONF") AS
  select t.db_name,
       t.db_constr,
       t.db_id_etl,
       case when t.db_id_etl is not null then nvl(t.db_user_etl,t.db_user_ds) end db_user_etl,
       case when t.db_id_etl is not null then nvl(t.db_pass_etl,t.db_pass_ds) end db_pass_etl,
       t.db_paral_etl,
       t.db_id_ds,
       case when t.db_id_ds is not null then nvl(t.db_user_ds,t.db_user_etl) end db_user_ds,
       case when t.db_id_ds is not null then nvl(t.db_pass_ds,t.db_pass_etl) end db_pass_ds,
       t.db_paral_ds,
       t.db_start,
       t.db_start_type,
       t.db_judge_sql,
       t.db_judge_pre,
       t.db_remark,
       t.did,
       t.bvalid,
       db_name || decode(bvalid, 'N', '_已废弃') as sys_name,       case         when bvalid = 'Y' then replace(            coalesce( regexp_substr(db_constr,'([0-9]{1,3}\.){3}[0-9]{1,3}:[0-9]+')  --正常ip和端口的写法                      ,replace(regexp_substr(db_constr,'//[^/]+'),'//') )   --域名的方式              , ':', ',')       end as netchk,       upper(substr(regexp_substr(db_constr, '^jdbc:.{1}'), -1)) as db_kind,       replace(regexp_substr(db_constr, '^jdbc:[^:]+'), 'jdbc:') as db_kind_full,       t.conf  from tb_imp_db t;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_DS2" ("DS_ID", "DS_NAME", "DEST_SYSID", "TASK_GROUP", "PARAM_SOU", "RETRY_CNT", "RUN_FREQ", "BVALID", "FLAG", "START_TIME", "END_TIME", "RUNTIME", "BRUN", "BDELAY", "BFREQ", "BPLAN", "PRE_SH", "POST_SH", "PRE_SQL", "POST_SQL", "INIT_RDS") AS
  select t.ds_id,a.ds_name,t.dest_sysid,t.task_group,t.param_sou,t.retry_cnt,t.run_freq,
       case when t.flag<>'X' then 1 else 0 end bvalid,
       case when t.flag='X' then 'X'
            else case when t.start_time between x.td and x.ntd then t.flag else 'N' end
       end flag,
       case when t.start_time between x.td and x.ntd then t.start_time end start_time,
       case when t.start_time between x.td and x.ntd and t.end_time>=t.start_time then t.end_time end end_time,
       t.runtime,
       case when (flag='N' or (flag='E' and retry_cnt>0))
                 and
                 (nvl(a.tbl_cnt,0)>0 or a.db_kind_full='file') then 1
       else 0 end brun,
       case when t.start_time < x.td and sysdate > t.start_time + (
            case when regexp_like(t.task_group,'^[A-Z0-9]{2}$') then to_date(gettd(),'YYYYMMDD')-to_date(getltd(),'YYYYMMDD') else to_date(getntd(),'YYYYMMDD')-to_date(gettd(),'YYYYMMDD') end
            ) then 1 else 0 end bdelay,
       stg01.fn_imp_freqchk(run_freq) bfreq,
       case when pn_type is not null and (pn_fixed is not null or pn_interval is not null) then 1 else 0 end bplan,
       case when pre_sh is not null then stg01.fn_imp_param_replace(pre_sh,param_sou) end pre_sh,
       case when post_sh is not null then stg01.fn_imp_param_replace(post_sh,param_sou) end post_sh,
       case when nvl(pre_sql,'T') not in('T','D') or t.dest_sysid='TM' then a.d_conn_full || chr(10) || decode(dest_sysid,'TM',a.pre_bak,stg01.fn_imp_param_replace(pre_sql,param_sou)) end pre_sql,
       case when post_sql is not null then a.d_conn_full || chr(10) || replace(replace(stg01.fn_imp_param_replace(post_sql,param_sou),'${task_group}',task_group),'${start_time}',to_char(start_time,'YYYY-MM-DD HH24:MI:SS')) end post_sql,
       --redis
       'rds "set ds.'||t.ds_id||'.paral_num '||nvl(t.paral_num,1)||'";'||
       case when pre_sh is not null then 'rds "set ds.'||t.ds_id||'.pre_sh 1";' end||
       case when post_sh is not null then 'rds "set ds.'||t.ds_id||'.post_sh 1";' end||
       case when a.db_kind_full<>'file' then
         case when t.bupdate='Y' then 'rds "set ds.'||t.ds_id||'.bupdate 1";' end||
         case when nvl(has_dsview,0)=1 then 'rds "set ds.'||t.ds_id||'.dsview 1";' end ||
         case when nvl(pre_sql,'T') not in('T','D') or a.pre_bak is not null then 'rds "set ds.'||t.ds_id||'.pre_sql 1";' end||
         case when post_sql is not null then 'rds "set ds.'||t.ds_id||'.post_sql 1";' end
       end init_rds
from tb_imp_ds2 t
inner join stg01.vw_imp_tradetime x on 1=1
inner join (select ds_id,db_kind_full,ds_name,d_conn_full,
                   max(case when bvalid=1 and sou_istab=0 and flag='N' then 1 else 0 end) has_dsview,
                   sum(case when bvalid=1 and nvl(sou_table,dest_tablename) is not null then 1 else 0 end) tbl_cnt,
                   replace(wm_concat(case when bvalid=1 and dest_sysid='TM' then 'create table ias_bak.'||dest_tablename||to_char(sysdate,'_MMDDHH24MISS')||' as select * from ias.'||dest_tablename end),',',chr(10)) pre_bak
              from vw_imp_ds2_mid group by ds_id,db_kind_full,ds_name,d_conn_full
     ) a on a.ds_id=t.ds_id;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_DS2_MID" ("DS_ID", "DS_NAME", "TASK_GROUP", "SOU_ISHDP", "SOU_ALLSQL", "SOU_TABLE", "SOU_FILTER", "SOU_ISTAB", "DEST_SYSID", "DEST_SYSNAME", "COL_MAP", "PARAM_SOU", "RETRY_CNT", "D_CONN", "D_USER", "D_PASS", "D_CONN_FULL", "DB_KIND", "DB_KIND_FULL", "DEST_OWNER", "DEST_TABLENAME", "FLAG", "TBL_ID", "PRE_SQL", "POST_SQL", "MAX_RUNTIME", "START_TIME", "END_TIME", "RUNTIME", "SOU_DB_CONN", "DSVIEW", "BVALID", "START_TIME_REAL", "END_TIME_REAL") AS
  select t.ds_id,
       b.sys_name||'-'||regexp_substr(task_group,'^[^,]+') as ds_name,
       t.task_group,
       case when a.sou_ishdp in('1','2') then '1' else '0' end sou_ishdp,
       case when a.sou_ishdp = '2' then 1 else 0 end sou_allsql,
       a.sou_table,
       a.sou_filter,
       case when regexp_like(upper(a.sou_table), '^[0-9A-Z_]+\.[0-9A-Z_]+$') then 1 else 0 end sou_istab,
       t.dest_sysid,
       b.sys_name as dest_sysname,
       nvl(a.col_map, t.col_map) col_map,
       t.param_sou,
       t.retry_cnt,
       b.db_constr as d_conn,
       b.db_user as d_user,
       b.db_pass as d_pass,
       b.db_user||chr(10)||b.db_pass||chr(10)||b.db_constr d_conn_full,
       b.db_kind,
       b.db_kind_full,
       coalesce(a.dest_owner, t.dest_owner, b.db_user,replace(b.db_constr,'jdbc:file:')) dest_owner,
       a.dest_tablename,
       nvl(a.flag, 'N') flag,
       a.tbl_id,
       --只需要继承主表的truncate和delete属性;文件推送时，该字段代表输出格式
       coalesce(a.pre_sql,case when t.pre_sql in('T','D') or b.db_kind_full='file' then t.pre_sql end,'T') pre_sql,
       a.post_sql,
       coalesce(a.max_runtime,t.max_runtime,3600) max_runtime,
       case when a.start_time >= t.start_time and a.start_time between x.td and x.ntd then a.start_time end start_time,
       case when a.end_time >= a.start_time and a.end_time >= t.start_time and a.end_time between x.td and x.ntd then a.end_time end end_time,
       case when a.end_time > a.start_time then trunc((a.end_time - a.start_time) * 24 * 60 * 60) end runtime,
       lower(dest_sysid || '_' || regexp_substr(task_group,'^[^,]+')) sou_db_conn,
       lower('v' || a.tbl_id) dsview,
       case when nvl(a.flag,'N')='X' or nvl(t.flag,'N')='X' then 0 else 1 end as bvalid,
       --基表的真实时间字段
       a.start_time as start_time_real,a.end_time as end_time_real
  from tb_imp_ds2 t
 inner join vw_imp_system b on b.sysid = t.dest_sysid
 inner join stg01.vw_imp_tradetime x on 1=1
 left join tb_imp_ds2_tbls a on t.ds_id = a.ds_id;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_DS2_NEEDS" ("DS_ID", "TASK_GROUP", "FLAG", "START_TIME", "END_TIME", "BMULTI", "NEEDS", "BOVER") AS
  with t_ds as (select ds_id,task_group,flag,start_time,end_time,case when regexp_like(task_group,'^[A-Z]+') and length(task_group)>2 then 1 else 0 end bmulti from vw_imp_ds2 where bvalid=1),
    t_grp as (
        select distinct *
        from (
            select t.*,regexp_substr(task_group,'([A-Z0-9]{2}|[a-z0-9]+$)',1,LEVEL) needs
            from (select * from t_ds where bmulti=1) t
            connect by level<=5
        ) where needs is not null
        union all
        select t.*,task_group
        from t_ds t where bmulti=0
        )
select t."DS_ID",t."TASK_GROUP",t."FLAG",t."START_TIME",t."END_TIME",t."BMULTI",t."NEEDS",case when a.task_group is not null then 1 else 0 end bover from t_grp t
left join vw_imp_taskgroup_over a on a.task_group=t.needs;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_ETL" ("WKF", "SYSID", "SYS_NAME", "SYSNAME", "DB_START", "DB_START_DT", "SOU_DB_CONN", "SOU_DB_KIND", "SOU_DB_CONSTR", "SOU_DB_USER", "SOU_DB_PASS", "SOU_DB_CONF", "SOU_OWNER", "SOU_TABLENAME", "SOU_FILTER", "SOU_SPLIT", "DEST_OWNER", "DEST_TABLENAME", "DEST_PART_KIND", "FLAG", "PARAM_SOU", "BUPDATE", "BCREATE", "ETL_KIND", "RETRY_CNT", "START_TIME", "END_TIME", "RUNTIME", "RUNTIME_ADD", "TID", "RID", "SPNAME", "DEST", "BPREVIEW", "BTDH", "BREALTIME", "REALTIME_INTERVAL", "REALTIME_INTERVAL_RANGE", "REALTIME_TASKGROUP", "REALTIME_FIXED", "BAFTER_RETRY", "AFTER_RETRY_FIXED", "AFTER_RETRY_PNTYPE", "BRUN", "BVALID", "BCJ", "JOBKIND") AS
  select 'WF_ODS'||sou_sysid||'_ETL' wkf,
       a.sysid,
       a.sys_name,
       a.sysid||'_'||a.sys_name sysname,
       nvl(a.db_start,a.start_kind) as db_start,
       a.db_start_dt,
       'DB_'||sou_sysid sou_db_conn,
       a.db_kind_full as sou_db_kind,
       a.db_constr sou_db_constr,
       a.db_user sou_db_user,
       a.db_pass sou_db_pass,
       a.conf sou_db_conf,
       nvl(decode(t.etl_kind,'R',t.realtime_sou_owner),t.sou_owner) sou_owner,
       case when t.sou_tablename like '%${%' then to_char(stg01.fn_imp_param_replace(t.sou_tablename,t.param_sou)) else t.sou_tablename end as sou_tablename,
       nvl(decode(t.etl_kind,'R',t.realtime_sou_filter),t.sou_filter) sou_filter,
       t.sou_split,
       'ODS'||sou_sysid dest_owner,
       t.dest_tablename,
       t.dest_part_kind,
       case
         when t.start_time between x.td and x.ntd then
          t.flag
         else
          decode(t.flag, 'X', 'X', 'N')
       end flag,
       t.PARAM_SOU,
       t.BUPDATE,
       t.BCREATE,
       t.etl_kind,
       t.retry_cnt,
       case
         when t.start_time between x.td and x.ntd then
          t.start_time
       end start_time,
       case
         when t.start_time between x.td and x.ntd and t.end_time>=t.start_time then
          t.end_time
       end end_time,
       t.runtime,
       t.runtime_add,
       t.tid,
       t.rowid rid,
       'hadoop_' || 'ODS'||sou_sysid || '_' || t.dest_tablename spname,
       'ODS' || sou_sysid || '.' || t.dest_tablename dest,
       t.bpreview,
       t.btdh,
       case
         when t.realtime_interval > 0 or t.realtime_fixed is not null then
          1
         else
          0
       end brealtime,
       t.realtime_interval,
       t.realtime_interval_range,
       t.realtime_taskgroup,
       t.realtime_fixed,
       case when t.after_retry_pntype is not null and t.after_retry_fixed is not null then 1 else 0 end bafter_retry,
       t.after_retry_fixed,
       t.after_retry_pntype,
       case
         when a.bvalid = 1 and (flag='N' or (retry_cnt > 0 and flag = 'E')) then
          1
         else
          0
       end brun,
       case
         when flag = 'X' or a.bvalid = 0 then
          0
         else
          1
       end bvalid,
       1 bcj,
       a.db_kind||'2H' jobkind
  from stg01.tb_imp_etl t
 inner join stg01.vw_imp_tradetime x
    on 1 = 1
 inner join stg01.vw_imp_system a
    on a.sysid = t.sou_sysid
   and a.sys_kind = 'etl';
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_ETL_COLS" ("TID", "JOBKIND", "DATA_TYPE", "COLUMN_NAME", "BQUOTA", "COL_NAME", "COL_TYPE", "COL_IDX", "DBID") AS
  select t.tid,x.jobkind,a.data_type,a.column_name_orig as column_name, --必须用原始字段名
       case when c.entry_value is not null or upper(a.column_name_orig)<>a.column_name or regexp_like(a.column_name_orig,'^[0-9]+$') then 1 else 0 end bquota, --字段名修改了或者特殊字符，JSON中需要加引号       lower(t.col_name) col_name,lower(t.col_type) col_type,t.col_idx,x.sysid as dbid  from stg01.tb_imp_tbl_hdp t inner join stg01.vw_imp_etl x on x.tid=t.tid  left join stg01.tb_imp_tbl_sou a on a.tid=t.tid and a.column_name=t.col_name  left join stg01.tb_dictionary c on c.entry_code='2014' and c.entry_value=a.column_name --数据库关键字 where t.col_idx<1000;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_ETL_COLTYPE" ("COLTYPE", "HIVE_TYPE", "REMARK") AS
  select entry_value coltype,entry_content hive_type,remark from stg01.tb_dictionary t
where entry_code='2011'
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_ETL_JUDGE" ("SYSID", "SYS_NAME", "DB_NAME", "JUDGE_SQL", "JUDGE_PRE", "BSTART", "FVAL", "JUDGE_TIME", "PX", "PARAM_VALUE", "DB_CONN") AS
  select t.sysid,
       t.sys_name,
       t.db_name,
       to_char(stg01.fn_imp_param_replace(t.db_judge_sql)) judge_sql,
       to_char(stg01.fn_imp_param_replace(t.db_judge_pre)) judge_pre,
       nvl(c.fval,-1) bstart,
       a.fval,
       a.dw_clt_date judge_time,
       row_number()over(partition by t.sysid order by a.dw_clt_date desc) px,
       x.param_value,
       t.db_conn
from stg01.vw_imp_system t
inner join stg01.vw_imp_param x on x.param_sou='C' and x.param_kind_0='TD'
left join stg01.tb_imp_flag a on a.kind='ETL_JUDGE' and a.fid=t.sysid and a.tradedate=x.param_value
left join (select fid,fval,row_number() over(partition by fid order by dw_clt_date desc) px from stg01.vw_imp_flag where kind='ETL_START') c on c.fid=t.sysid and c.px=1
where t.sys_kind = 'etl' and t.db_judge_sql is not null
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_ETL_OVERPREC" ("SYSNAME", "DB_START", "DB_START_DT", "TOTAL_CNT", "OVER_CNT", "OVER_PREC", "RUN_CNT", "ERR_CNT", "NO_CNT", "WAIT_CNT", "START_TIME_LTD", "END_TIME_LTD", "START_TIME_TD", "END_TIME_TD", "START_TIME_R", "END_TIME_R", "RUNTIME_LTD", "RUNTIME_TD", "RUNTIME_R") AS
  with t_flag as(select fid,
                     max(case when param_kind_0='LTD' and fval=3 then dw_clt_date end) start_time_ltd,
                     max(case when param_kind_0='LTD' and fval=4 then dw_clt_date end) end_time_ltd,
                     max(case when param_kind_0='TD' and fval=3 then dw_clt_date end) start_time_td,
                     max(case when param_kind_0='TD' and fval=4 then dw_clt_date end) end_time_td
              from tb_imp_flag t
             inner join (select param_kind_0,param_value from vw_imp_param where param_sou='C' and param_kind_0 in('LTD','TD')) a on a.param_value=t.tradedate
              where kind in('ETL_START','ETL_END') and fval in(3,4)
              group by fid)
select t.sysname,t.db_start,t.db_start_dt,t.total_cnt,t.over_cnt,t.over_prec,t.run_cnt,t.err_cnt,t.no_cnt,t.wait_cnt,t.start_time_ltd,t.end_time_ltd,t.start_time_td,t.end_time_td,t.start_time_r,t.end_time_r,
       trunc((end_time_ltd-start_time_ltd)*24*60*60) runtime_ltd,
       trunc((end_time_td-start_time_td)*24*60*60) runtime_td,
       trunc((end_time_r-start_time_r)*24*60*60) runtime_r
from (
select sysname,db_start,db_start_dt,
       count(1) total_cnt,
       sum(decode(flag,'Y',1,0)) over_cnt,
       sum(decode(flag,'Y',1,0))/count(1) over_prec,
       sum(decode(flag,'R',1,0)) run_cnt,
       sum(decode(flag,'E',1,0)) err_cnt,
       sum(decode(flag,'N',1,0)) no_cnt,
       sum(decode(flag,'W',1,0)) wait_cnt,
       min(a.start_time_ltd) start_time_ltd,
       max(a.end_time_ltd) end_time_ltd,
       min(a.start_time_td) start_time_td,
       max(a.end_time_td) end_time_td,
       min(case when start_time>end_time_td then start_time end) start_time_r,
       max(case when end_time>end_time_td and flag='Y' then end_time end) end_time_r
  from stg01.vw_imp_etl t
  left join t_flag a on a.fid=t.sysid
 where bvalid = 1
 group by sysid,sysname,db_start,db_start_dt
) t
order by t.db_start_dt,t.sysname
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_ETL_SOUTAB" ("KIND", "SOU_DB_CONN", "COL_JSON") AS
  with t_sql as  --获取（采集源库，数据服务目标库）的表结构信息
     (select kind,sou_db_conn,
             'delete from stg01.tb_imp_etl_soutab where sou_db_conn='''||sou_db_conn||''''||
                     case when count(1)<=30 then ' and upper(owner||''.''||table_name) in('||to_char(wm_concat(''''||upper(sou_owner||'.'||sou_tablename)||''''))||')' end pre_sql,
             'select '||case when substr(max(flag),1,1)='1' then 'distinct ' end||''''||sou_db_conn||''' sou_db_conn,'||
             case when sou_db_kind = 'oracle' then
               't.owner,t.table_name,t.column_name,data_type,data_length,data_precision,data_scale,column_id,a.table_type,a.comments tab_comment,b.comments col_comment '||
               'from all_tab_cols t '||
               'left join all_tab_comments a on a.owner=t.owner and a.table_name=t.table_name '||
               'left join all_col_comments b on b.owner=t.owner and b.table_name=t.table_name and b.column_name=t.column_name '||
               'where column_id is not null and t.owner||''.''||t.table_name in('
             when sou_db_kind in('sqlserver','mysql','postgresql') then
               case when substr(max(flag),1,1)='1' then 'TABLE_CATALOG' else 'table_schema' end||
               ' as owner,TABLE_NAME table_name,COLUMN_NAME,upper(DATA_TYPE) data_type,'||
               'CHARACTER_MAXIMUM_LENGTH data_length,NUMERIC_PRECISION data_precision,NUMERIC_SCALE data_scale,'||
               'ORDINAL_POSITION column_id,'''' table_type,'''' tab_comment,'''' col_comment from '||
               case when substr(max(flag),1,1)='1' then to_char(replace('(select * from '||wm_concat(distinct sou_owner||'.'),',','INFORMATION_SCHEMA.COLUMNS union all select * from ')||'INFORMATION_SCHEMA.COLUMNS) x') else 'INFORMATION_SCHEMA.COLUMNS' end ||
               ' where upper('||
               (select nvl(regexp_substr(regexp_substr(conf,'\[soutab_tbl:[^]]+'),'[^:]+$'),'concat(table_schema,''.'',table_name)')
               from vw_imp_system where lower(sysid) in(regexp_substr(lower(sou_db_conn),'^[^_]+'),lower(replace(sou_db_conn,'DB_'))))
               ||') in('
             when sou_db_kind = 'inceptor2' then
               'database_name as owner,table_name,column_name,upper(column_type) data_type,column_length data_length,column_length data_precision,'||
               'column_scale data_scale,column_id,'''' table_type,'''' tab_comment,'''' col_comment from system.columns_v '||
               'where upper(concat(database_name,''.'',table_name)) in('
             when sou_db_kind = 'clickhouse' then
               'database as owner,table as table_name,name as column_name,type as data_type from system.columns where upper(database||''.''||table) in('
             end || wm_concat(''''||upper(sou_owner||'.'||sou_tablename)||'''')||')' colsql
      from (select 'etl' as kind,sou_db_conn,sou_db_kind,sou_owner,sou_tablename,
                   case when sou_db_kind='sqlserver' and sou_db_conf like '%[soutab_owner:table_catalog]%' then '1' end flag --sqlserver是用的table_catalog作为用户名
              from stg01.vw_imp_etl
           where sysid<>'ZD'
             and (bupdate='Y'
                 or sysid in (select sou_sysid from tb_imp_etl group by sou_sysid having sum(case when bupdate='Y' then 1 else 0 end)>30)  --如果一个数据源仅30个表需要更新,仅获取指定表;如果超过30个,则获取所有表的结构,避免表结构被presql删除没被获取
                 or (select count(1) from tb_imp_etl where bupdate='Y')=0    --如果没有表需要更新,则更新全部表
                 )
           union all
           select 'ds',sou_db_conn,db_kind_full,dest_owner,dest_tablename,null from stg01.vw_imp_ds2_mid
           where db_kind_full<>'file'
      ) t
      group by kind,sou_db_kind,sou_db_conn ),
  t_hdp as  --获取hadoop所有视图及表的字段信息
  (select 'ds' as kind,ds_id as sou_db_conn,'tb_imp_etl_tbls' dest_tbl,'jdbc:trino://infa01:18080/hive' sou_conn,'' sou_pass,
          'delete from stg01.tb_imp_etl_tbls where db_name=''ds'' and tbl_name in('||regexp_replace(listagg(''''||dsview||''',')within group(order by dsview),',$',')') pre_sql,
          '' post_sql,
          'select db_name,tbl_name,col_idx,col_name from default.vw_presto_view where db_name=''ds'' and tbl_name in('''||replace(wm_concat(dsview),',',''',''')||''')' colsql
     from vw_imp_ds2_mid
     where sou_istab=0
     group by ds_id
    union all
   select 'etl','hadoop','tb_imp_etl_tbls_tmp','jdbc:mysql://nn01:3306/hive','ZEQEJGsNP7NT',
           'truncate table stg01.tb_imp_etl_tbls_tmp',
           'begin stg01.sp_imp_alone(''get_hdptbls'');end;',
           to_clob('select db_id,db_name,db_location,tbl_id,tbl_name,tbl_type,tbl_location,cd_id,col_name,col_type,col_comment,col_idx,tbl_comment from vw_tab_cols where not(tbl_type=''VIRTUAL_VIEW'' and col_name=''dummy'')')
      from dual)
--获取（采集源库，数据服务目标库）的表结构信息
select t.kind,t.sou_db_conn,
       replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(to_clob(c.entry_content),
           '${s_conn}',b.db_constr),'${s_user}',b.db_user),'${s_pass}',b.db_pass),'${s_sql}',colsql),
           '${d_conn}','jdbc:oracle:thin:@inidb:1521/ini'),'${d_user}','stg01'),'${d_pass}','stg01pwd'),'${d_tblname}','stg01.tb_imp_etl_soutab'),
           '${d_pre}',t.pre_sql),'${d_post}','') col_json
from t_sql t
inner join stg01.vw_imp_system b on b.db_name=t.sou_db_conn or upper(b.sysid)=regexp_replace(upper(t.sou_db_conn),'_[0-9A-Z]+$')
inner join stg01.tb_dictionary c on c.entry_code='5005' and c.entry_value='DS_JSON'
where b.bvalid=1	--数据源必须正常，否则获取表结构会失败
union all
--获取hadoop所有视图及表的字段信息
select kind,sou_db_conn,
       replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(
                               (select to_clob(entry_content) from stg01.tb_dictionary where entry_code='5005' and entry_value='DS_JSON'),
             '${s_conn}',sou_conn),'${s_user}','hive'),'${s_pass}',sou_pass),'${s_sql}',colsql),
             '${d_conn}','jdbc:oracle:thin:@inidb:1521/ini'),'${d_user}','stg01'),'${d_pass}','stg01pwd'),'${d_tblname}','stg01.'||dest_tbl),
             '${d_pre}',pre_sql),'${d_post}',post_sql)
from t_hdp;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_FLAG" ("TRADEDATE", "KIND", "FID", "FVAL", "DW_CLT_DATE") AS
  select t."TRADEDATE",t."KIND",t."FID",t."FVAL",t."DW_CLT_DATE"
  from tb_imp_flag t
 inner join stg01.vw_imp_param x
    on x.param_sou = 'C'
   and x.param_kind_0 = 'TD'
   and to_number(x.param_value) = t.tradedate
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_JOBFILE" ("JOBKIND", "JOBFILE") AS
  select entry_value jobkind,
       replace(replace(entry_content,
                       '${r' || substr(entry_value, 1, 1) || '}',
                       (select entry_content
                          from stg01.tb_dictionary a
                         where a.entry_code = '5001'
                           and a.ENTRY_VALUE =
                               'r' || substr(t.entry_value, 1, 1))),
               '${w' || substr(entry_value, -1) || '}',
               (select entry_content
                  from stg01.tb_dictionary a
                 where a.entry_code = '5001'
                   and a.ENTRY_VALUE = 'w' || substr(t.entry_value, -1))) jobfile
  from stg01.tb_dictionary t
 where entry_code = '5000'
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_JY_NEEDBAK" ("TBL") AS
  select lower(dest_tablename) as tbl from stg01.tb_imp_etl t
inner join stg01.tb_imp_sp_com a on a.sp_id=t.tid and a.com_idx=120 and a.com_kind='presto' and a.flag!='X'
where t.sou_sysid='JY' and t.flag!='X' ;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_PARAM" ("PARAM_SOU", "PARAM_KIND_0", "PARAM_KIND", "PARAM_NAME", "PARAM_REMARK", "PARAM_VALUE") AS
  select param_sou,
       param_kind_0,
       param_kind,
       param_name,
       param_remark,
       param_value
  from stg01.tb_imp_param0
 where param_kind_0 is not null
union all
select a.param_sou,
       entry_value param_kind_0,
       '${' || entry_value || '}' param_kind,
       '$$' || entry_value as param_name,
       '自定义参数:'||remark,
       nvl(entry_content, a.param_value)
  from stg01.tb_dictionary t
 inner join (select param_sou, param_value
               from stg01.tb_imp_param0
              where param_kind_0 = 'TD') a
    on 1 = 1
  left join stg01.tb_imp_param0 b
    on b.param_kind_0 = t.entry_value
 where entry_code = '2005'
   and b.param_kind_0 is null
union all
select param_sou,
       'T1',
       '${T1}',
       '$$T_1',
       'T-1日,不与切日挂钩',
       case when td=to_char(sysdate,'YYYYMMDD') then ltd else td end
from (
select param_sou,
       max(case when param_kind_0='TD' then param_value end) as td,
       max(case when param_kind_0='LTD' then param_value end) as ltd
from stg01.tb_imp_param0 t
where param_kind_0 is not null and param_kind_0 in('TD','LTD')
group by param_sou
)
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_PARAM_ALL" ("CD", "CM0", "CM1", "CQ0", "CQ1", "CW0", "CW1", "CWM", "CWS", "CY0", "CY1", "L10TD", "L180TD", "L20TD", "L2M0", "L2M1", "L2Q0", "L2Q1", "L2TM", "L2TY", "L2Y0", "L2Y1", "L30TD", "L40TD", "L5TD", "L5TDM", "L60TD", "L90TD", "LD180", "LD180T", "LD30", "LD30T", "LD365", "LD365T", "LD730", "LD730T", "LD800T", "LD90", "LD90T", "LM0", "LM1", "LQ0", "LQ1", "LTD", "LTM", "LTQ", "LTY", "LW0", "LW1", "LY0", "LY1", "LYTD", "NTD", "TD", "TF", "TM", "TQ", "TY") AS
  select "CD","CM0","CM1","CQ0","CQ1","CW0","CW1","CWM","CWS","CY0","CY1","L10TD","L180TD","L20TD","L2M0","L2M1","L2Q0","L2Q1","L2TM","L2TY","L2Y0","L2Y1","L30TD","L40TD","L5TD","L5TDM","L60TD","L90TD","LD180","LD180T","LD30","LD30T","LD365","LD365T","LD730","LD730T","LD800T","LD90","LD90T","LM0","LM1","LQ0","LQ1","LTD","LTM","LTQ","LTY","LW0","LW1","LY0","LY1","LYTD","NTD","TD","TF","TM","TQ","TY" from (select param_kind_0,param_value from tb_imp_param0 where param_sou='C') pivot (max(param_value) for param_kind_0 in('CD' as CD,'CM0' as CM0,'CM1' as CM1,'CQ0' as CQ0,'CQ1' as CQ1,'CW0' as CW0,'CW1' as CW1,'CWM' as CWM,'CWS' as CWS,'CY0' as CY0,'CY1' as CY1,'L10TD' as L10TD,'L180TD' as L180TD,'L20TD' as L20TD,'L2M0' as L2M0,'L2M1' as L2M1,'L2Q0' as L2Q0,'L2Q1' as L2Q1,'L2TM' as L2TM,'L2TY' as L2TY,'L2Y0' as L2Y0,'L2Y1' as L2Y1,'L30TD' as L30TD,'L40TD' as L40TD,'L5TD' as L5TD,'L5TDM' as L5TDM,'L60TD' as L60TD,'L90TD' as L90TD,'LD180' as LD180,'LD180T' as LD180T,'LD30' as LD30,'LD30T' as LD30T,'LD365' as LD365,'LD365T' as LD365T,'LD730' as LD730,'LD730T' as LD730T,'LD800T' as LD800T,'LD90' as LD90,'LD90T' as LD90T,'LM0' as LM0,'LM1' as LM1,'LQ0' as LQ0,'LQ1' as LQ1,'LTD' as LTD,'LTM' as LTM,'LTQ' as LTQ,'LTY' as LTY,'LW0' as LW0,'LW1' as LW1,'LY0' as LY0,'LY1' as LY1,'LYTD' as LYTD,'NTD' as NTD,'TD' as TD,'TF' as TF,'TM' as TM,'TQ' as TQ,'TY' as TY));
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_PARAM_FILE" ("PARAM_FILE", "PARAM_CONTENT") AS
  select decode(param_sou,'C','param_sys','N','param_sys_next','L','param_sys_yest','T','param_sys_test') param_file,
       replace('[Global],'||wm_concat(param_name||'='||param_value),',',chr(10)) param_content
from vw_imp_param
group by param_sou
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_PLAN" ("PN_ID", "PN_TYPE", "PN_TYPE_NAME", "PN_FIXED", "DT_FULL", "PN_INTERVAL", "PN_RANGE", "SPNAME", "FLAG", "START_TIME", "END_TIME", "RUNTIME", "BRUN", "BPNTYPE") AS
  select pn_id,
       pn_type,
       stg01.fn_imp_pnname(pn_type) pn_type_name,
       pn_fixed,
       b.dt_full,
       pn_interval,
       pn_range,
       stg01.fn_imp_pnname(pn_type,pn_fixed,pn_interval,pn_range) spname,
       case
         when t.start_time between x.td and x.ntd then
          t.flag
         else 'N'
       end flag,
       case
         when t.start_time between x.td and x.ntd then
          t.start_time
       end start_time,
       case
         when t.end_time between x.td and x.ntd then
          t.end_time
       end end_time,
       runtime,
       case when flag<>'R' and pn_type<>'9'
             and (select count(1) from tb_imp_sp_com where sp_id = t.pn_id and nvl(flag,'N')<>'X')>0
             and stg01.fn_imp_timechk(sysdate, pn_fixed, pn_interval, pn_range,nvl(bexit,'Y'))=1
       then 1 else 0 end brun,
       stg01.fn_imp_pntype(pn_type) bpntype
  from tb_imp_plan t
 inner join vw_imp_tradetime x on 1=1
 left join vw_imp_date b on b.dt=regexp_substr(t.pn_fixed,'\d+')
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_PLAN_ALL" ("PN_TYPE", "DT_FULL", "PN_TYPE_NAME", "SPNAME", "PN_FLAG", "C1", "C1F", "C2", "C2F", "C3", "C3F", "C4", "C4F", "C5", "C5F", "C6", "C6F") AS
  with t_pn as
	(select t.pn_id,t.pn_type_name,t.pn_type,t.dt_full,t.spname,t.flag as pn_flag,t.start_time as pn_start,t.end_time as pn_end,
    to_char(regexp_substr(a.com_text,'[^-#'||chr(10)||']+'))||a.flag com_name,
    row_number()over(partition by t.pn_id order by a.com_idx) px
  from vw_imp_plan t
  inner join tb_imp_sp_com a on a.sp_id=t.pn_id)
select pn_type,dt_full,pn_type_name,spname,pn_flag,
  substr(c1,1,length(c1)-1) c1,substr(c1,-1) c1f,
  substr(c2,1,length(c2)-1) c2,substr(c2,-1) c2f,
  substr(c3,1,length(c3)-1) c3,substr(c3,-1) c3f,
  substr(c4,1,length(c4)-1) c4,substr(c4,-1) c4f,
  substr(c5,1,length(c5)-1) c5,substr(c5,-1) c5f,
  substr(c6,1,length(c6)-1) c6,substr(c6,-1) c6f
from (select pn_id,pn_type,dt_full,pn_type_name,spname,pn_flag,px,com_name from t_pn)
pivot(max(com_name) for px in(1 as c1,2 as c2,3 as c3,4 as c4,5 as c5,6 as c6))
union all
select pn_type,(select dt_full from vw_imp_date where dt=regexp_substr(pn_fixed,'\d+')),
       stg01.fn_imp_pnname(pn_type),stg01.fn_imp_pnname(pn_type,pn_fixed,pn_interval,pn_range),flag,'数据服务:'||to_char(stg01.fn_imp_value('taskname',ds_id)),flag,
       null,null,null,null,null,null,null,null,null,null
from tb_imp_ds2
where pn_type is not null and flag<>'X'
union all
select pn_type,(select dt_full from vw_imp_date where dt=regexp_substr(pn_fixed,'\d+')),
       stg01.fn_imp_pnname(pn_type),stg01.fn_imp_pnname(pn_type,pn_fixed,pn_interval,pn_range),flag,'SP计算:'||to_char(stg01.fn_imp_value('taskname',sp_id)),flag,
       null,null,null,null,null,null,null,null,null,null
from tb_imp_sp
where pn_type is not null and flag<>'X'
order by 1,2,4
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_REALTIMES" ("TID", "TIMES") AS
  with t_interval as --根据轮询时间，计算出全天的具体执行时间点(不涉及具体的表，只计算时间)
 (select realtime_interval, dt_full as times
    from (select realtime_interval
            from stg01.tb_imp_etl
           where nvl(realtime_interval, 0) > 0
           group by realtime_interval) t
   inner join vw_imp_date a
      on mod(substr(a.dt_full, 1, 2) * 60 + substr(a.dt_full, -2),
             t.realtime_interval) = 0 and a.dt_full between '0800' and '1800'),
t_fixed as --计算所有的定点时间(不涉及具体的表，只计算时间)
 (select distinct to_char(regexp_substr(fixed, '[^,]+', 1, level)) fixed
    from (select wm_concat(distinct realtime_fixed) fixed
            from stg01.tb_imp_etl
           where realtime_fixed is not null)
  connect by level <= regexp_count(fixed, ',') + 1)
select t.tid, a.times
  from tb_imp_etl t
 inner join t_interval a
    on a.realtime_interval = t.realtime_interval
   and a.times between
       (select dt_full from vw_imp_date where dt = nvl(regexp_substr(t.realtime_interval_range, '^\d+'), '8'))
       and
       (select dt_full from vw_imp_date where dt = nvl(regexp_substr(t.realtime_interval_range, '\d+$'), '18'))
 where nvl(t.realtime_interval, 0) > 0
union all
select t.tid, b.dt_full
  from tb_imp_etl t
 inner join t_fixed a
    on instr(',' || t.realtime_fixed || ',', ',' || a.fixed || ',') > 0
 inner join vw_imp_date b
    on regexp_replace(b.dt_full, '(^0+|00$)') = a.fixed
 where t.realtime_fixed is not null
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_REALTIMES_ETL" ("LAST_TIMES", "NEXT_TIMES", "SPNAME", "START_TIME", "END_TIME", "FLAG") AS
  select max(case when a.times<=to_char(sysdate,'HH24MI') then times end) last_times,
       min(case when a.times>to_char(sysdate,'HH24MI') then times end) next_times,
       t.dest spname,t.start_time,t.end_time,t.flag
from vw_imp_etl t
inner join vw_imp_realtimes a on a.tid=t.tid and a.times between to_char(sysdate-1/2/24,'HH24MI') and to_char(sysdate+1/2/24,'HH24MI')
where stg01.fn_imp_pntype('2')=1 --交易日当天
group by t.dest,t.start_time,t.end_time,t.flag
order by 2,1 desc,3
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_SP" ("SP_OWNER", "SP_NAME", "SP_ID", "RID", "FLAG", "RUN_FREQ", "START_TIME", "END_TIME", "RETRY_CNT", "RUNTIME", "REALTIME_TASKGROUP", "NEED_SOU", "NEED_SP", "SP_ALLTABS", "SP_DEST", "THROUGH_NEED_SOU", "THROUGH_NEED_SP", "TASK_GROUP", "PARAM_SOU", "REMARK", "SPNAME", "BVALID", "BRUN", "BFREQ", "BPLAN") AS
  select lower(t.sp_owner) sp_owner,
       lower(t.sp_name) sp_name,
       t.sp_id,
       t.rowid as rid,
       case
         when t.start_time between x.td and x.ntd then
          t.flag
         else
          decode(t.flag, 'X', 'X', 'N')
       end flag,
       t.RUN_FREQ,
       case
         when t.start_time between x.td and x.ntd then
          t.start_time
       end start_time,
       case
         when t.start_time between x.td and x.ntd and t.end_time>=t.start_time then
          t.end_time
       end end_time,
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
       lower(sp_owner || '_' || sp_name) spname,
       case when flag <> 'X' then 1 else 0 end bvalid,
       case when flag='N' or (retry_cnt > 0 and flag = 'E') then 1 else 0 end brun,
       stg01.fn_imp_freqchk(run_freq) bfreq,
       case when pn_type is not null and (pn_interval>0 or pn_fixed is not null) then 1 else 0 end bplan
  from stg01.tb_imp_sp t
 inner join stg01.vw_imp_tradetime x
    on 1 = 1
  left join stg01.tb_imp_sp_needall a
    on a.sp_id = t.sp_id
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_SP_DEST_EQUAL" ("SP_ID", "DEST") AS
  with t_dest as
 (select sp_id, table_name
    from tb_imp_sp_needtab t
   where kind = 'DEST'
     and table_name not like 'XXX%'
     and sp_id not in (select sp_id from vw_imp_sp where bvalid = 0)
  union all
  select sp_id, upper(sp_owner || '.' || sp_name)
    from vw_imp_sp
   where bvalid = 1)
select t.sp_id, t.table_name as dest
  from t_dest t
 inner join (select table_name
               from t_dest t
              inner join (select upper(db_name||'.'||tbl_name) tbls from tb_imp_etl_tbls group by db_name,tbl_name
                ) a on a.tbls=t.table_name
              group by table_name
             having count(1) > 1) a
    on a.table_name = t.table_name;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_SP_NEEDS" ("SP_ID", "FLAG", "START_TIME", "NEEDS", "NEEDS_FLAG", "NEEDS_END_TIME") AS
  with t_need_run as
 ( --SP执行情况
  select sp_id, flag, end_time
    from stg01.vw_imp_sp
   where bvalid = 1
     and bfreq = 1
  union all
  --数据源采集情况
  select sysid,
         case when a.fid is not null then 'Y' else 'N' end flag,
         dw_clt_date
    from stg01.vw_imp_system t
    left join stg01.vw_imp_flag a
      on a.kind = 'ETL_END'
     and a.fval = 4
     and a.FID = t.sysid
   where t.sys_kind = 'etl')
select t.sp_id,
       t.flag,
       t.start_time,
       b.sp_id      needs,
       b.flag       needs_flag,
       b.end_time   needs_end_time
  from stg01.vw_imp_sp t
 inner join stg01.tb_imp_sp_needtab a
    on a.sp_id = t.sp_id
   and a.kind = 'NEEDS'
 inner join t_need_run b
    on b.sp_id = a.table_name
 where t.bvalid = 1
   and t.bfreq = 1
   and t.bplan = 0
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_SP_SOURCE" ("PARENT_ID", "CATE_ID", "CATE_NAME", "CATE_VALUE") AS
  with t_sp as
 (select b.sp_id parent_id,
         b.sp_owner || '.' || b.sp_name parent_name,
         nvl(a.sp_id, sys_guid()) cate_id,
         case
           when a.sp_id is not null then
            a.sp_owner || '.' || a.sp_name
           else
            t.table_name
         end cate_name
    from (select sp_id, lower(table_name) table_name
            from stg01.tb_imp_sp_needtab
           where kind = 'ALL'
          minus
          select sp_id, lower(table_name) table_name
            from stg01.tb_imp_sp_needtab
           where kind = 'DEST') t
    left join tb_imp_sp a
      on a.sp_owner || '.' || a.sp_name = t.table_name
    left join tb_imp_sp b
      on b.sp_id = t.sp_id)
select parent_id,cate_id,cate_name,1 cate_value from t_sp
union all
select distinct null,parent_id,parent_name,1 from t_sp
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_SYSTEM" ("SYS_KIND", "SYSID", "SYS_NAME", "DB_NAME", "DB_CONSTR", "NETCHK", "BVALID", "DB_USER", "DB_PASS", "DB_PARAL", "DB_KIND", "DB_KIND_FULL", "CONF", "START_KIND", "DB_START_TYPE", "DB_START", "DB_START_DT", "CREATE_DB", "DB_JUDGE_SQL", "DB_JUDGE_PRE", "DB_CONN", "DB_CONN_CMD") AS
  with t_dt as --根据配置的db_start,计算出具体的时间,多个时间的只算第一个
 (select a.db_start,t.dt_full
  from vw_imp_date t
 inner join (select db_start,
                    regexp_substr(db_start, '^\d+') db_start_dt
               from tb_imp_db where db_start is not null
               group by db_start) a
    on a.db_start_dt = t.dt)
select 'etl' as sys_kind,
       db_id_etl as sysid,
       sys_name,
       'DB_'||db_id_etl as db_name,
       t.db_constr,
       netchk,
       case t.bvalid when 'Y' then 1 else 0 end bvalid,
       db_user_etl db_user,
       db_pass_etl db_pass,
       nvl(db_paral_etl,10) as db_paral,
       db_kind,
       db_kind_full,
       conf,
       case when t.db_judge_sql is not null then '标志' else '定时' end start_kind,
       t.db_start_type,
       t.db_start,
       a.dt_full as db_start_dt,
       lower('create database if not exists ods' || t.db_id_etl ||
             ' location ''/ods/ods' || t.db_id_etl || ''';') create_db,
       t.db_judge_sql,
       t.db_judge_pre,
       '-U "'||db_constr||'" -u "'||db_user_etl||'" -p "'||db_pass_etl||'"' as db_conn,
       case db_kind
        when 'M'    --mysql
            then 'mysql -h'||regexp_substr(netchk,'^[^,]+')||' -P'||regexp_substr(netchk,'[^,]+$')||' -u'||db_user_etl||' -p'''||db_pass_etl||''' '||regexp_substr(regexp_replace(db_constr,'\?.*$'),'[0-9a-zA-Z_]+$')
        when 'O'    --oracle
            then 'sqlplus '||db_user_etl||'/'||db_pass_etl||'@'||regexp_replace(regexp_substr(db_constr,'@.+$'),'@([^:]+):([^:]+)(:|/)([^:]+)','\1:\2/\4') end as db_conn_cmd
from vw_imp_db t
left join t_dt a on a.db_start=t.db_start
where db_id_etl is not null
union all
select 'ds' as sys_kind,
       db_id_ds as sysid,
       sys_name,
       db_id_ds as db_name,
       db_constr,
       netchk,
       case bvalid when 'Y' then 1 else 0 end bvalid,
       db_user_ds,
       db_pass_ds,
       nvl(db_paral_ds,3),
       db_kind,
       db_kind_full,
       conf,
       null,null,null,null,null,null,null,
       '-U "'||db_constr||'" -u "'||db_user_ds||'" -p "'||db_pass_ds||'"',
       case db_kind
        when 'M'    --mysql
            then 'mysql -h'||regexp_substr(netchk,'^[^,]+')||' -P'||regexp_substr(netchk,'[^,]+$')||' -u'||db_user_ds||' -p'''||db_pass_ds||''' '||regexp_substr(regexp_replace(db_constr,'\?.*$'),'[0-9a-zA-Z_]+$')
        when 'O'    --oracle
            then 'sqlplus '||db_user_ds||'/'||db_pass_ds||'@'||regexp_replace(regexp_substr(db_constr,'@.+$'),'@([^:]+):([^:]+)(:|/)([^:]+)','\1:\2/\4') end as db_conn_cmd
from vw_imp_db
where db_id_ds is not null;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_SYSTEM_ALLSQL" ("DB_KIND_FULL", "DB_CONSTR", "DB_USER", "DB_PASS", "SYSID") AS
  select db_kind_full,
       db_constr,
       db_user_etl as db_user,
       db_pass_etl as db_pass,
       lower(db_id_etl) as sysid
  from vw_imp_db
 where db_id_etl is not null
   and netchk is not null
union all
select db_kind_full, db_constr, db_user_ds, db_pass_ds, lower(db_id_ds)
  from vw_imp_db
 where db_id_ds is not null
   and netchk is not null;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TASKGROUP" ("TASK_GROUP") AS
  with t_tgs as --所有配置的任务组汇总为一个字段，便于下一步拆分
 (select wm_concat(task_group) task_group
    from (select task_group
            from stg01.tb_imp_sp
           where task_group is not null
           group by task_group
          union all
          select realtime_taskgroup
            from stg01.tb_imp_etl
           where realtime_taskgroup is not null
           group by realtime_taskgroup)),
t_tg as --将任务组拆分为一行一个
 (select to_char(regexp_substr(task_group, '[^,]+', 1, level)) task_group
    from t_tgs t
  connect by level <= regexp_count(task_group, ',') + 1)
select distinct task_group from t_tg
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TASKGROUP_DETAIL" ("TASK_GROUP", "KIND", "ALLCNT", "YCNT", "START_TIME", "END_TIME", "RUNTIME", "RCNT", "START_TIME_R", "NCNT", "ECNT", "PREC", "KIND2", "TASK_GROUP2", "DS_NAME", "FLAG2", "START_TIME2", "END_TIME2", "BFLAG", "FLAG_TIME", "ERRMSG") AS
  with t_tg_pre as	--生成任务组的任务
(select t.task_group,a.kind,
		count(1) allcnt,
		sum(case when flag='Y' then 1 else 0 end) ycnt,
		min(case when flag='Y' then a.start_time end) start_time,
		max(case when flag='Y' then a.end_time end) end_time,
		least(sum(runtime),round((max(case when flag='Y' then a.end_time end)-min(case when flag='Y' then a.start_time end))*24*60*60)) runtime,
		sum(case when flag='R' then 1 else 0 end) rcnt,
		min(case when flag='R' then a.start_time end) start_time_r,
		sum(case when flag='N' then 1 else 0 end) ncnt,
		sum(case when flag='E' then 1 else 0 end) ecnt,
    round(sum(case when flag in('Y') then 1 else 0 end)/count(1),4) prec
	from vw_imp_taskgroup t
	inner join (
		select 'SP计算' kind,task_group,flag,start_time,end_time,runtime
      from vw_imp_sp
     where task_group is not null and bvalid=1 and bfreq=1
     union all
		select '实时采集',realtime_taskgroup,flag,start_time,end_time,runtime
      from vw_imp_etl
     where realtime_taskgroup is not null and bvalid=1
	) a on instr(','||a.task_group||',' , ','||t.task_group||',')>0
	group by t.task_group,a.kind),
t_tg_post as	--任务组调起的后续服务
(select '数据服务' kind2,regexp_replace(task_group,'[A-Z0-9]{2}') task_group2,ds_name,flag flag2,start_time start_time2,
		case when flag in('E','Y') then end_time else start_time+runtime/24/60/60 end end_time2
   from vw_imp_ds2
  where length(task_group)>2
  union all
 select 'SP计算' kind,realtime_taskgroup,spname,flag,start_time,
 		case when flag in('E','Y') then end_time else start_time+runtime/24/60/60 end
   from vw_imp_sp
  where bvalid=1 and realtime_taskgroup is not null )
select t."TASK_GROUP",t."KIND",t."ALLCNT",t."YCNT",t."START_TIME",t."END_TIME",t."RUNTIME",t."RCNT",t."START_TIME_R",t."NCNT",t."ECNT",t.prec,a."KIND2",a."TASK_GROUP2",a."DS_NAME",a."FLAG2",a."START_TIME2",a."END_TIME2",
	case when b.fid is not null then '是' else '' end bflag,
	b.dw_clt_date flag_time,
	case when allcnt<>ycnt and b.fid is not null then '未执行完就生成标志'
		when b.fid is not null and start_time2<b.dw_clt_date then '没有调起后续服务就完成标志'
		when (b.fid is null or rcnt>0) and flag2='R' then '服务已经启动，标志基础任务未完成'
		when nvl(prec,0)>1 then '数据服务执行超时'
		when ecnt>0 then '生成任务组的任务报错'
    when flag2='E' then '任务组调起的服务报错'
	end errmsg
from t_tg_pre t
left join t_tg_post a on a.task_group2=t.task_group
left join vw_imp_flag b on b.kind='TASK_GROUP' and b.fid=t.task_group;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TASKGROUP_OVER" ("TASK_GROUP") AS
  select a.task_group
  from (select task_group, flag
          from stg01.vw_imp_sp
         where task_group is not null
           and bvalid = 1
           and bfreq =1
        union
        select realtime_taskgroup, flag
          from stg01.vw_imp_etl
         where realtime_taskgroup is not null
           and etl_kind = 'R'
           and bvalid = 1) t
 inner join stg01.vw_imp_taskgroup a
    on instr(',' || t.task_group || ',', ',' || a.task_group || ',') > 0
 group by a.task_group
having count(1) = sum(case when flag = 'Y' then 1 else 0 end)
union all
--ODS采集成功
select t.sysid
  from vw_imp_system t
 inner join (select fid,
                    fval,
                    dw_clt_date,
                    row_number() over(partition by fid order by dw_clt_date desc) px
               from vw_imp_flag
              where kind = 'ETL_END') a
    on a.fid = t.sysid
   and a.px = 1
   and a.fval = 4
  left join (select sysid
               from stg01.vw_imp_etl
              where bvalid = 1
              group by sysid
             having sum(case when flag <> 'Y' then 1 else 0 end) > 0) b
    on b.sysid = t.sysid
 where t.sys_kind = 'etl'
   and b.sysid is null
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TBL" ("TID", "SOU_DB_CONN", "SOU_OWNER", "SOU_TABLENAME", "COLUMN_NAME_ORIG", "COLUMN_NAME", "COLUMN_ID", "DATA_TYPE", "DATA_LENGTH", "DATA_PRECISION", "DATA_SCALE", "TABLE_COMMENT", "COLUMN_COMMENT", "DEST_TYPE", "DEST_TYPE_FULL", "HIVE_OWNER", "HIVE_TABLENAME", "COL_NAME", "COL_TYPE_FULL", "COL_TYPE", "COL_PRECISION", "COL_SCALE", "COL_IDX", "TBL_COMMENT", "COL_COMMENT", "CD_ID") AS
  with t_tbl as (
select nvl(t.tid,a.tid) as tid,
       --源表的所有信息
       a.sou_db_conn,a.sou_owner,a.sou_tablename,a.column_name_orig,a.column_name,a.column_id,a.data_type,a.data_length,a.data_precision,a.data_scale,a.tbl_comment as table_comment,a.col_comment as column_comment,a.dest_type,a.dest_type_full,
       --hadoop表的所有信息
       t.hive_owner,t.hive_tablename,t.col_name,t.col_type_full,t.col_type,t.col_precision,t.col_scale,t.col_idx,t.tbl_comment,t.col_comment,t.cd_id
from tb_imp_tbl_hdp t
full join tb_imp_tbl_sou a on a.tid=t.tid and t.col_name=a.column_name)
select "TID","SOU_DB_CONN","SOU_OWNER","SOU_TABLENAME","COLUMN_NAME_ORIG","COLUMN_NAME","COLUMN_ID","DATA_TYPE","DATA_LENGTH","DATA_PRECISION","DATA_SCALE","TABLE_COMMENT","COLUMN_COMMENT","DEST_TYPE","DEST_TYPE_FULL","HIVE_OWNER","HIVE_TABLENAME","COL_NAME","COL_TYPE_FULL","COL_TYPE","COL_PRECISION","COL_SCALE","COL_IDX","TBL_COMMENT","COL_COMMENT","CD_ID" from t_tbl
where tid not in(
select tid from t_tbl	--hadoop中已经存在的表
group by tid
having count(1)=sum(case when hive_owner is null then 1 else 0 end)
)
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TBL_DIFF_HIVE" ("KIND", "TID", "ALTER_SQL") AS
  select '增加字段' as kind,tid,       lower('alter table `' || (select replace(dest,'.','`.`') from stg01.vw_imp_etl where tid=t.tid) ||'` add columns(' ||       wm_concat('`' || column_name || '` ' || dest_type_full || case when nvl(t.column_comment,' ')<>' ' then ' comment "'||t.column_comment||'"' end ) ||       ');') alter_sqlfrom vw_imp_tbl twhere col_name is nullgroup by tidunion allselect '字段类型修改' as kind,t.tid,      to_clob(lower('alter table '||a.hive_owner||'.`'||a.hive_tablename||'` change `'||a.col_name||'` `'||a.col_name||'` '||       t.dest_type||       case when t.dest_type='DECIMAL' then '('||            case when t.data_precision>nvl(a.col_precision,0) or t.data_scale>nvl(a.col_scale,0) then                 case when t.data_precision-a.col_precision<t.data_scale-a.col_scale then least(38,a.col_precision+t.data_scale-a.col_scale) else t.data_precision end            end||','|| case when t.data_scale>nvl(a.col_scale,0) then t.data_scale else nvl(a.col_scale,0) end||')'       end || case when nvl(t.col_comment,' ')<>' ' then ' comment "'||t.col_comment||'"' end || ';' )) alter_sqlfrom stg01.tb_imp_tbl_sou tinner join stg01.tb_imp_tbl_hdp a on a.tid=t.tid and t.column_name=a.col_namewhere t.dest_type_full<>a.col_type_full and not      (        (t.dest_type in('INT','BIGINT') and a.col_type in('INT','BIGINT'))        or        (t.dest_type='DECIMAL' and a.col_type='DECIMAL' and a.col_precision>=t.data_precision and a.col_scale>=t.data_scale)      )union allselect '修改表注释',t.tid,       'alter table `' ||(select to_clob(lower(replace(dest,'.','`.`'))) from stg01.vw_imp_etl where tid=t.tid) ||       '` set tblproperties("comment"="' || t.table_comment || '");'from (select tid,max(tbl_comment) as table_comment from stg01.tb_imp_tbl_sou t group by tid) tinner join (select tid,max(tbl_comment) as tbl_comment from stg01.tb_imp_tbl_hdp group by tid      ) a on a.tid=t.tidwhere nvl(a.tbl_comment,' ') <> t.table_comment;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TBL_DIFF_MYSQL" ("KIND", "TID", "ALTER_SQL") AS
  select '修改字段注释' kind,tid,
       to_clob('/*'||(select lower(dest) from stg01.vw_imp_etl where tid=t.tid)||'*/ '||
                'update hive.COLUMNS_V2 set comment='''||column_comment||''' where cd_id = '||cd_id||' and integer_idx = '||(col_idx-1)||';') alter_sql
from vw_imp_tbl t
where nvl(col_comment,' ')<>column_comment and cd_id is not null;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TBL_HDP" ("TID", "BUPDATE", "HIVE_OWNER", "HIVE_TABLENAME", "COL_NAME", "COL_TYPE_FULL", "COL_TYPE", "COL_PRECISION", "COL_SCALE", "COL_IDX", "TBL_COMMENT", "COL_COMMENT", "CD_ID") AS
  select t.tid,
       t.bupdate,
       t.dest_owner hive_owner,
       t.dest_tablename hive_tablename,
       upper(a.col_name) col_name,
       upper(a.col_type) col_type_full,
       --类似于源端数据库，将合并的字段类型拆分为三列，便于后续对比
       upper(regexp_substr(a.col_type,'^[^(]+')) col_type,
       cast(case when col_type like 'decimal%' then regexp_substr(col_type,'\d+') end as int) col_precision,
       cast(case when col_type like 'decimal%' then regexp_substr(replace(col_type,')'),'\d+$') end as int) col_scale,
       col_idx,
       to_char(stg01.fn_imp_comment_replace(tbl_comment)) tbl_comment,
       to_char(stg01.fn_imp_comment_replace(col_comment)) col_comment,
       --hadoop表的专属字段，用于修改字段注释
       a.cd_id
  from stg01.vw_imp_etl t
 inner join stg01.tb_imp_etl_tbls a
    on upper(a.db_name) = upper(t.dest_owner)
   and upper(a.tbl_name) = upper(t.dest_tablename);
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TBL_SOU" ("TID", "BUPDATE", "SOU_DB_CONN", "SOU_OWNER", "SOU_TABLENAME", "COLUMN_NAME_ORIG", "COLUMN_NAME", "COLUMN_ID", "DATA_TYPE", "DATA_LENGTH", "DATA_PRECISION", "DATA_SCALE", "TBL_COMMENT", "COL_COMMENT", "DEST_TYPE", "DEST_TYPE_FULL") AS
  with t_sou as
 (select t.tid,
         t.bupdate,
         t.sou_db_conn,
         upper(t.sou_owner) sou_owner,
         upper(t.sou_tablename) sou_tablename,
         --原始表的字段名称(保留大小写，避免字段为关键字时，无法恢复原状)
         a.column_name as column_name_orig,
         --修改源库的字段名：1、数据中心新增的四个字段，如果源库有同名的；2、源库的字段名为中文
         case when upper(a.column_name) in(select upper(entry_value) from stg01.tb_dictionary where entry_code='2015' union all select 'LOGDATE' from dual) then upper(a.column_name)||'_RENAME'              when regexp_like(upper(a.column_name), '[^A-Z0-9_]+') then 'COLUMN_' || column_id         else upper(column_name) end column_name,         a.column_id,         case when a.data_type='NUMBER' and a.data_precision is null and a.data_scale=0 then 'INT'              else regexp_substr(a.data_type, '[a-zA-Z0-9_]+') end data_type,         a.data_length,         case when nvl(a.data_precision, 38) > 38 then 38 else nvl(a.data_precision, 38) end data_precision,         nvl(a.data_scale, 10) data_scale,         to_char(stg01.fn_imp_comment_replace(tab_comment)) tbl_comment,         --源库字段名为中文的，字段名改了后，原有中文名放在注释中         case when regexp_like(upper(a.column_name), '[^A-Z0-9_]+') then '=>' || column_name || '<=' else '' end ||              to_char(stg01.fn_imp_comment_replace(col_comment)) col_comment    from stg01.vw_imp_etl t   inner join stg01.tb_imp_etl_soutab a      on a.sou_db_conn = t.sou_db_conn     and upper(a.owner) = upper(t.sou_owner)     and upper(a.table_name) = upper(t.sou_tablename)     )select x.TID,x.BUPDATE,x.SOU_DB_CONN,x.SOU_OWNER,x.SOU_TABLENAME,x.COLUMN_NAME_ORIG,x.COLUMN_NAME,x.COLUMN_ID,x.DATA_TYPE,x.DATA_LENGTH,x.DATA_PRECISION,x.DATA_SCALE,x.TBL_COMMENT,x.COL_COMMENT,x.DEST_TYPE,		case         when dest_type = 'DECIMAL' then          dest_type || '(' || data_precision || ',' || data_scale || ')'         else          dest_type       end dest_type_full  from (select t.tid,               t.bupdate,               t.sou_db_conn,               t.sou_owner,               t.sou_tablename,               t.column_name_orig,               t.column_name,               t.column_id,               t.data_type,               t.data_length,               t.data_precision,               t.data_scale,               t.tbl_comment,               t.col_comment,               upper(nvl(c.hive_type, 'string')) dest_type          from t_sou t          left join stg01.vw_imp_etl_coltype c            on upper(c.coltype) = t.data_type) x;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_IMP_TRADETIME" ("TD", "NTD") AS
  with t_param as
 (select param_kind_0, param_value
    from stg01.tb_imp_param0
   where param_sou = 'C'     and param_kind_0 in ('TD', 'NTD'))select (select to_date(param_value || ' 163000', 'YYYYMMDD HH24MISS')          from t_param         where param_kind_0 = 'TD') td,       (select to_date(param_value || ' 163000', 'YYYYMMDD HH24MISS')          from t_param         where param_kind_0 = 'NTD') ntd  from dual;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_MOBILE_GROUP" ("GROUPID", "MOBILE", "USERNAME") AS
  with t_gp as
 (select nvl(entry_content,' ') groupid, entry_value mobile, remark username
    from stg01.tb_dictionary
   where entry_code = '1011'
     and regexp_like(entry_value, '^1[0-9]{10}$'))
select distinct regexp_substr(groupid, '[^,]+', 1, level) groupid,
                mobile,
                username
  from t_gp
connect by level <= regexp_count(groupid, ',+') + 1
 order by 1
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_TMP" ("DB_NAME", "DB_CONSTR", "DB_ID_ETL", "DB_USER_ETL", "DB_PASS_ETL", "DB_PARAL_ETL", "DB_ID_DS", "DB_USER_DS", "DB_PASS_DS", "DB_PARAL_DS", "DB_START", "DB_START_TYPE", "DB_JUDGE_SQL", "DB_JUDGE_PRE", "DB_REMARK", "DID", "BVALID", "CONF") AS
  select t."DB_NAME",t."DB_CONSTR",t."DB_ID_ETL",t."DB_USER_ETL",t."DB_PASS_ETL",t."DB_PARAL_ETL",t."DB_ID_DS",t."DB_USER_DS",t."DB_PASS_DS",t."DB_PARAL_DS",t."DB_START",t."DB_START_TYPE",t."DB_JUDGE_SQL",t."DB_JUDGE_PRE",t."DB_REMARK",t."DID",t."BVALID",t."CONF" from stg01.tb_imp_db t
inner join (select max(db_id_etl) as maxid from stg01.tb_imp_db
	) a on a.maxid=t.db_id_etl;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_TRADE_DATE" ("INIT_DATE", "PX") AS
  select cast(to_number(entry_value, '99999999') as number(10)) init_date,
       dense_rank() over(order by entry_value) px
  from stg01.tb_dictionary
 where entry_code = '1021'
;
;

CREATE OR REPLACE FORCE VIEW "STG01"."VW_UPDT_RDS" ("RDS") AS
  select 'set param.'||param_kind_0||' "'||param_value||'"' rds
from stg01.vw_imp_param
where param_sou='C' and param_kind_0 is not null
union all
select 'set '||decode(entry_code,'1061','shname','1062','path','1063','com')||'.'||entry_value||' "'||entry_content||'"' from stg01.tb_dictionary
where entry_code in('1061','1062','1063')
order by 1
;
;
