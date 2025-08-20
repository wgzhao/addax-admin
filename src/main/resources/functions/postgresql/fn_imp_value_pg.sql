-- sql
CREATE OR REPLACE FUNCTION fn_imp_value(
  i_kind  text,
  i_sp_id text DEFAULT '',
  i_value1 text DEFAULT ''
) RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
   o_return  text;
   ctmp1     text;
   ctmp2     text;
   strtmp1   text;
   strtmp2   text;
   v_tradedate int;
   c1  record;
   tbl record;
   col record;
BEGIN
   o_return := '';
   strtmp1 := '';
   strtmp2 := '';
   ctmp1 := '';
   ctmp2 := '';
   v_tradedate := gettd();

   -- 计划运行列表获取
   IF i_kind = 'plan_run' THEN
      WITH t_sp AS (
        SELECT 'plan|'||pn_id AS sp_id
          FROM vw_imp_plan
         WHERE brun=1 AND bpntype=1
        UNION ALL
        SELECT 'judge|'||CASE bstart WHEN -1 THEN 'status_' WHEN 0 THEN 'start_' ELSE '' END||sysid
          FROM vw_imp_etl_judge
         WHERE bstart IN(-1,0) AND px=1
      )
      SELECT string_agg(sp_id, chr(10)) INTO o_return FROM t_sp;

   -- 存储过程运行列表
   ELSIF i_kind = 'sp_run' THEN
     WITH t_sp AS (
       SELECT 'sp' AS kind, sp_id, 'sp'||sp_owner AS dest_sys, runtime, brun
         FROM vw_imp_sp
        WHERE brun = 1 OR flag = 'R'
       UNION ALL
       SELECT 'etl', tid, sysid, runtime+runtime_add, brun
         FROM vw_imp_etl
        WHERE brun = 1 OR flag = 'R'
       UNION ALL
       SELECT 'ds', ds_id, 'ds'||dest_sysid, coalesce(runtime,999), brun
         FROM vw_imp_ds2
        WHERE brun = 1 OR flag = 'R'
     ),
     ranked AS (
       SELECT
         (CASE WHEN kind='etl' THEN 'sp' ELSE kind END)||'|'||sp_id AS sp_id,
         brun * row_number() OVER (ORDER BY brun, runtime + 20000 / sys_px DESC) AS px
       FROM (
         SELECT kind, sp_id, dest_sys, brun, runtime,
                row_number() OVER (PARTITION BY kind, dest_sys ORDER BY brun, runtime DESC) AS sys_px
         FROM t_sp
       ) s
       WHERE sys_px <= coalesce((SELECT db_paral FROM vw_imp_system WHERE sysid = dest_sys AND sys_kind='etl'), 8)
     )
     SELECT string_agg(sp_id, chr(10) ORDER BY px) INTO o_return
       FROM ranked
      WHERE px BETWEEN 1 AND 100;

   -- COM存储过程获取
   ELSIF i_kind = 'com_text' THEN
     SELECT com_text INTO o_return
       FROM tb_imp_sp_com
      WHERE com_id = i_sp_id;

     IF o_return IS NOT NULL THEN
       SELECT coalesce(a.param_sou,b.param_sou,'C'), b.tid
         INTO strtmp1, strtmp2
         FROM tb_imp_sp_com t
         LEFT JOIN tb_imp_sp a ON a.sp_id = t.sp_id
         LEFT JOIN tb_imp_etl b ON b.tid = t.sp_id
        WHERE t.com_id = i_sp_id;

       IF strtmp2 IS NOT NULL THEN
         SELECT
           replace(
           replace(
           replace(
           replace(
           replace(
           replace(
           replace(
           replace(
           replace(
           replace(
             o_return,
             '${sou_dbcon}', t.sou_db_constr),
             '${sou_user}', t.sou_db_user),
             '${sou_pass}', t.sou_db_pass),
             '${sou_tblname}', CASE WHEN t.sou_owner LIKE '%-%' THEN '`'||t.sou_owner||'`' ELSE t.sou_owner END ||
                               CASE WHEN sou_db_kind='sqlserver' AND t.sou_db_conf LIKE '%[soutab_owner:table_catalog]%' THEN '..' ELSE '.' END ||
                               t.sou_tablename),
             '${sou_filter}', replace(replace(t.sou_filter,'\','\\'),'"','\"')),
             '${sou_split}', t.sou_split),
             '${tag_tblname}', '/ods/'||lower(replace(t.dest,'.','/'))||'/logdate=${dest_part}'),
             '${dest_part}', fn_imp_value('dest_part',t.tid)),
             '${modifier_no}', replace(replace(replace(t.sou_filter,'''',''''''),'\','\\'),'"','\"')),
             '${hdp_cols}', (SELECT string_agg(col_name, ',' ORDER BY col_idx) FROM tb_imp_tbl_hdp WHERE tid=t.tid AND col_name<>'LOGDATE')
           )
           INTO o_return
           FROM vw_imp_etl t
          WHERE t.tid = strtmp2;
       END IF;

       o_return := fn_imp_param_replace(o_return, strtmp1);
     END IF;

   --- 生成 addax 的 job.json
   ELSIF i_kind = 'jobfile' THEN
     SELECT a.jobfile, a.jobkind INTO o_return, strtmp1
       FROM vw_imp_etl t
       JOIN vw_imp_jobfile a ON a.jobkind=t.jobkind
      WHERE t.tid = i_sp_id;

     ctmp1 := '';
     ctmp2 := '';
     FOR c1 IN
       SELECT jobkind, data_type, column_name, bquota, col_name, col_type, col_idx
         FROM vw_imp_etl_cols
        WHERE tid = i_sp_id
        ORDER BY col_idx
     LOOP
       -- 输入列
       ctmp1 := ctmp1 || '"' ||
         CASE upper(c1.col_name)
           WHEN 'DW_CLT_DATE' THEN
             '''${NOW}''' || CASE WHEN substr(c1.jobkind,1,1) = 'P' THEN '::varchar' ELSE '' END
           WHEN 'MODIFIER_NO' THEN
             '''${modifier_no}''' || CASE WHEN substr(c1.jobkind,1,1) = 'P' THEN '::varchar' ELSE '' END
           WHEN 'DW_TRADE_DATE' THEN
             '${TD}' || CASE WHEN substr(c1.jobkind,1,1) = 'P' THEN '::int' ELSE '' END
           ELSE
             CASE
               WHEN c1.data_type='ROWID' THEN 'rowidtochar('||c1.column_name||')'
               WHEN c1.column_name IS NULL AND substr(c1.jobkind,1,1) = 'P' THEN 'null::varchar'
               WHEN c1.bquota = 1 THEN
                 CASE WHEN substr(c1.jobkind,1,1)='M'
                      THEN '`'||c1.column_name||'`'
                      ELSE '\"'||c1.column_name||'\"' END
               ELSE coalesce(c1.column_name, 'null') END
         END || '",';

       -- hadoop 列定义
       ctmp2 := ctmp2 ||
         CASE
           WHEN substr(c1.jobkind,-1) = 'H' THEN
             '{"name":"' || lower(c1.col_name) || '","type":"' || c1.col_type || '"},'
           WHEN substr(c1.jobkind,1,1) = 'H' THEN
             '{"index":' || c1.col_idx || ',"type":"' ||
             CASE WHEN c1.col_type='decimal' THEN 'double'
                  WHEN c1.col_type IN('varchar','char') THEN 'string'
                  ELSE c1.col_type END || '"},'
         END;
     END LOOP;

     SELECT replace(
              replace(
                o_return,
                '${sou_col}',
                regexp_replace(CASE WHEN substr(t.jobkind,1,1)='H' THEN ctmp2 ELSE ctmp1 END, ',\s*$','')
              ),
              '${tag_col}',
              regexp_replace(CASE WHEN substr(t.jobkind,-1)='H' THEN ctmp2 ELSE ctmp1 END, ',\s*$','')
            )
       INTO o_return
       FROM vw_imp_etl t
      WHERE t.tid = i_sp_id;

     -- dbf
     IF strtmp1 = 'D2H' THEN
       SELECT string_agg(format('{"index":%s, "type":"string"}', col_idx), ',' ORDER BY col_idx) ||
              ',{"value":"${NOW}","type":"string"},{"value":"ZD","type":"string"},{"value":"${TD}","type":"long"}'
         INTO strtmp1
         FROM vw_imp_etl_cols t
        WHERE jobkind='D2H' AND tid = i_sp_id AND col_name NOT IN('dw_clt_date','modifier_no','dw_trade_date');
       o_return := replace(o_return, '${col}', strtmp1);
     END IF;

   -- ODS 全部成功的系统
   ELSIF i_kind = 'etl_end' THEN
      SELECT string_agg(sysid, chr(10))
        INTO o_return
      FROM (
        SELECT sysid
          FROM vw_imp_etl
         WHERE bvalid = 1
         GROUP BY sysid
         HAVING sum(CASE WHEN flag = 'Y' OR (flag = 'E' AND retry_cnt = 0) THEN 1 ELSE 0 END) = count(1)
            AND sum(CASE WHEN etl_kind='A' THEN 1 ELSE 0 END) = count(1)
      ) t
      LEFT JOIN (
        SELECT fid
          FROM tb_imp_flag t
          LEFT JOIN (
            SELECT sysid, max(end_time) AS maxtime
              FROM vw_imp_etl
             WHERE bvalid = 1
             GROUP BY sysid
          ) a
            ON a.sysid = t.fid
         WHERE tradedate = v_tradedate
           AND kind = 'ETL_END'
           AND (fval = '4' OR (fval = '5' AND dw_clt_date > maxtime))
         GROUP BY fid
      ) a
      ON a.fid = t.sysid
      WHERE a.fid IS NULL;

   -- 目标 logdate 分区获取
   ELSIF i_kind = 'dest_part' THEN
     SELECT CASE
              WHEN etl_kind = 'R' THEN '0'
              ELSE
                CASE
                  WHEN dest_part_kind IN ('D','M','Q') THEN '${T' || dest_part_kind || '}'
                  ELSE '1'
                END
            END
       INTO o_return
       FROM vw_imp_etl
      WHERE tid = i_sp_id;

   -- 目标 logdate 分区值
   ELSIF i_kind = 'dest_part_value' THEN
     SELECT coalesce(a.param_value, '1')
       INTO o_return
       FROM vw_imp_etl t
       LEFT JOIN vw_imp_param a
         ON a.param_sou = t.param_sou
        AND a.param_kind_0 LIKE 'T%'
        AND a.param_kind_0 = 'T' || t.dest_part_kind
      WHERE t.tid = i_sp_id;

   -- 系统名称
   ELSIF i_kind = 'sysname' THEN
     SELECT CASE WHEN i_value1='short' THEN sys_name ELSE sysid||'_'||sys_name END
       INTO o_return
       FROM vw_imp_system
      WHERE sysid = i_sp_id;

   -- 任务名称
   ELSIF i_kind = 'taskname' THEN
     SELECT spname INTO o_return
       FROM (
         SELECT spname FROM vw_imp_etl WHERE tid = i_sp_id
         UNION ALL
         SELECT spname FROM vw_imp_sp WHERE sp_id = i_sp_id
         UNION ALL
         SELECT spname FROM vw_imp_plan WHERE pn_id = i_sp_id
         UNION ALL
         SELECT ds_name AS spname FROM vw_imp_ds2_mid WHERE i_sp_id IN (ds_id, tbl_id)
         UNION ALL
         SELECT sysid ||'_' || sys_name AS spname FROM vw_imp_system WHERE sysid = i_sp_id
       ) s
       LIMIT 1;

   -- 跑批类型列表
   ELSIF i_kind = 'pntype_list' THEN
     SELECT string_agg(entry_content||'['||entry_value||']='||fn_imp_pntype(entry_value), chr(10) ORDER BY entry_value)
       INTO o_return
       FROM tb_dictionary
      WHERE entry_code='1064' AND entry_value<='3';

   -- schema 名称获取（hadoop + sp_owner）
   ELSIF i_kind = 'get_schema' THEN
     SELECT 'ODS[A-Z0-9]{2}' ||
            string_agg('|'||upper(db_name), '' ORDER BY length(db_name) DESC)
       INTO o_return
       FROM (
         SELECT db_name
           FROM tb_imp_etl_tbls
          WHERE col_idx = 1000
            AND db_name NOT IN ('edwuf','edwuftp','kpiuf','kpiuftp','tmp','default')
            AND db_name !~* '^(xds|ods)'
          GROUP BY db_name
         UNION
         SELECT sp_owner FROM vw_imp_sp WHERE bvalid=1 GROUP BY sp_owner
       ) s;

   -- ds\_sql / ds\_sql\_presto / ds\_sql\_allsql
   ELSIF i_kind IN('ds_sql','ds_sql_presto','ds_sql_allsql') THEN
      FOR c1 IN
        SELECT 'create or replace view ds.' || t.dsview || ' as ' || chr(10) ||
               fn_imp_param_replace(regexp_replace(t.sou_table, ';$', '') || ';', t.param_sou) AS ds_sql
          FROM vw_imp_ds2_mid t
         WHERE t.sou_istab = 0
           AND t.db_kind_full <> 'file'
           AND t.sou_allsql = (CASE WHEN i_kind='ds_sql_allsql' THEN 1 ELSE 0 END)
           AND t.flag = 'N'
           AND t.ds_id = i_sp_id
      LOOP
          o_return := coalesce(o_return,'') || c1.ds_sql || chr(10);
      END LOOP;

   -- ds\_json 生成 addax JSON
   ELSIF i_kind = 'ds_json' THEN
       WITH t_map AS (
         SELECT DISTINCT col_map,
                regexp_replace(s, '.*\s+(as|AS)\s+(\w+).*', '\2') AS col_map_name,
                regexp_replace(s, '\s+(as|AS)\s+\w+\s*$', '') AS col_map_define
         FROM (
           SELECT col_map, unnest(regexp_split_to_array(col_map, '\s*;\s*')) AS s
           FROM vw_imp_ds2_mid
           WHERE col_map IS NOT NULL AND tbl_id = i_sp_id
           GROUP BY col_map
         ) x
       ),
       t_db AS (
         SELECT tbl_id, col_name, col_define, col_type,
                row_number() OVER (PARTITION BY tbl_id, col_name ORDER BY kind DESC) AS px
         FROM (
           SELECT 0 AS kind, t.tbl_id, col_name, col_name AS col_define, col_type
             FROM vw_imp_ds2_mid t
             JOIN tb_imp_etl_tbls a
               ON (CASE WHEN t.sou_istab = 1 THEN lower(t.sou_table) ELSE lower('ds.'||t.dsview) END) = lower(a.db_name||'.'||a.tbl_name)
            WHERE t.tbl_id = i_sp_id AND t.sou_ishdp = '1'
           UNION ALL
           SELECT 0 AS kind, x.tbl_id, a.column_name, a.column_name, a.data_type
             FROM vw_imp_etl t
             JOIN vw_imp_ds2_mid x
               ON x.tbl_id = i_sp_id
              AND (CASE WHEN x.sou_istab = 1 THEN lower(x.sou_table) ELSE lower('ds.'||x.dsview) END) = lower(t.dest)
              AND x.sou_ishdp = '0'
             JOIN tb_imp_etl_soutab a
               ON a.sou_db_conn=t.sou_db_conn AND upper(a.owner)=upper(t.sou_owner) AND upper(a.table_name)=upper(t.sou_tablename)
           UNION ALL
           SELECT 1 AS kind, t.tbl_id, a.col_map_name, a.col_map_define, ' '
             FROM vw_imp_ds2_mid t
             JOIN t_map a ON a.col_map=t.col_map
            WHERE t.col_map IS NOT NULL AND t.tbl_id=i_sp_id
         ) u
       ),
       t_sql AS (
         SELECT t.tbl_id,
                -- 源列
                string_agg(
                   CASE WHEN a.col_define=a.col_name AND a.col_type='boolean' AND t.db_kind='O'
                        THEN 'cast('||a.col_name||' as int)'
                        ELSE a.col_define END
                 , ',' ORDER BY a.col_name) AS s_cols,
                -- 目标列
                string_agg(
                   CASE WHEN d.entry_value IS NOT NULL
                        THEN CASE WHEN t.db_kind = 'M' THEN '`'||b.column_name||'`' ELSE '\"'||b.column_name||'\"' END
                        ELSE b.column_name END
                 , ',' ORDER BY a.col_name) AS d_cols,
                -- 源过滤
                CASE
                  WHEN t.sou_ishdp::int = 0 THEN
                    ' from ' ||
                    max(c.sou_owner || CASE WHEN c.sou_db_kind='sqlserver' AND c.sou_db_conf LIKE '%[soutab_owner:table_catalog]%' THEN '..' ELSE '.' END || c.sou_tablename) ||
                    ' where ' || coalesce(t.sou_filter,'1=1')
                  WHEN t.sou_istab::int = 1 THEN
                    ' from '||t.sou_table||' where '||replace(coalesce(t.sou_filter, 'logdate=''${dest_part}'''::text), '${dest_part}', coalesce(fn_imp_value('dest_part',c.tid),'${TD}'))
                  ELSE
                    ' from ds.'||max(t.dsview)
                END AS s_filter,
                -- 源连接
                max(CASE WHEN t.sou_allsql=1 THEN 'jdbc:trino://etl01:18080/hive'
                         WHEN t.sou_ishdp='1' THEN 'jdbc:trino://etl01:18080/hive'
                         ELSE c.sou_db_constr END) AS s_conn,
                max(CASE WHEN t.sou_ishdp='1' THEN 'hive' ELSE c.sou_db_user END) AS s_user,
                max(CASE WHEN t.sou_ishdp='1' THEN '' ELSE c.sou_db_pass END) AS s_pass
           FROM vw_imp_ds2_mid t
           JOIN t_db a ON a.px=1 AND a.tbl_id = t.tbl_id
           JOIN tb_imp_etl_soutab b
             ON b.sou_db_conn = t.sou_db_conn
            AND lower(b.owner) = lower(t.dest_owner)
            AND lower(b.table_name) = lower(t.dest_tablename)
            AND lower(b.column_name) = lower(a.col_name)
           LEFT JOIN vw_imp_etl c ON lower(c.dest) = lower(t.sou_table)
           LEFT JOIN tb_dictionary d ON d.entry_code='2014' AND upper(d.entry_value)=upper(b.column_name)
          WHERE t.tbl_id=i_sp_id
          GROUP BY t.tbl_id, t.sou_table, t.sou_filter, c.tid, t.col_map, t.sou_istab, t.sou_ishdp, t.db_kind
       )
       SELECT replace(
                replace(
                  replace(
                    replace(
                      replace(
                        replace(
                          replace(
                            replace(
                              replace(
                                replace(
                                  c.jobfile::text,
                                  '${s_conn}', a.s_conn
                                ), '${s_user}', a.s_user
                              ), '${s_pass}', a.s_pass
                            ), '${s_sql}', fn_imp_param_replace('select '||a.s_cols||a.s_filter, t.param_sou)
                          ), '${d_presql}',
                             CASE
                               WHEN (t.retry_cnt<3 AND t.pre_sql='T') THEN 'delete from @table'
                               WHEN t.pre_sql='T' THEN 'truncate table @table'
                               WHEN t.pre_sql='D' THEN 'delete from @table'
                               WHEN t.pre_sql='N' THEN ''
                               ELSE fn_imp_param_replace(t.pre_sql, t.param_sou)
                             END
                        ), '${d_postsql}', fn_imp_param_replace(t.post_sql, t.param_sou)
                      ), '${d_cols}', a.d_cols
                    ), '${d_conn}', t.d_conn
                  ), '${d_user}', t.d_user
                ), '${d_pass}', t.d_pass
              ), '${d_tblname}', t.dest_owner||'.'||t.dest_tablename
           INTO o_return
           FROM vw_imp_ds2_mid t
           LEFT JOIN t_sql a ON a.tbl_id = t.tbl_id
           LEFT JOIN vw_imp_jobfile c ON c.jobkind='H2'||t.db_kind
          WHERE t.tbl_id=i_sp_id;

   -- ds\_cmd 生成执行命令
   ELSIF i_kind = 'ds_cmd' THEN
       SELECT CASE WHEN db_kind_full = 'file' THEN
                 'mkdir -p ' || regexp_replace(d.dest_dir || '/' || d.dest_file, '/[^/]+$', '') || chr(10) ||
                 '$(rds "get path.bin")/jdbc2console.sh -U "jdbc:trino://etl01:18080/hive" -u "hive" -p "" "' || d.sou_table || '" ' || d.output_format || ' -f Default >' ||
                 d.dest_dir || '/' || d.dest_file
              ELSE
                 '$(rds "get path.bin")/tuna.py -t ' || t.max_runtime || ' -m addax -f ${1}'
              END INTO o_return
         FROM vw_imp_ds2_mid t
         LEFT JOIN (
           SELECT tbl_id,
                  dest_owner AS dest_dir,
                  fn_imp_param_replace(dest_tablename, param_sou)::text AS dest_file,
                  fn_imp_param_replace(sou_table, param_sou)::text AS sou_table,
                  pre_sql AS output_format
             FROM vw_imp_ds2_mid
            WHERE db_kind_full = 'file'
              AND tbl_id = i_sp_id
         ) d
           ON d.tbl_id = t.tbl_id
       WHERE t.tbl_id = i_sp_id;

   -- DS 依赖表
   ELSIF i_kind = 'ds_needs' THEN
       SELECT string_agg(lower(table_name), chr(10) ORDER BY table_name)
              INTO o_return
       FROM tb_imp_sp_needtab
       WHERE kind='DS' AND sp_id = i_sp_id
       GROUP BY sp_id;

   -- SP 下一步要运行的 SP
   ELSIF i_kind = 'sp_allnext' THEN
       ctmp1 := '';
       ctmp2 := '';
       FOR c1 IN(
          WITH t_lev AS(
            SELECT t.*, b.spname, cast(substring(a.kind from '\d+$') as int) AS lev
              FROM tb_imp_sp_needtab t
              JOIN tb_imp_sp_needtab a ON a.sp_id = t.sp_id AND a.kind LIKE 'RUNLEV%'
              JOIN vw_imp_sp b ON b.sp_id = t.sp_id AND b.bvalid=1
             WHERE t.kind='NDS' AND t.table_name = i_sp_id
          )
          SELECT table_name, lev,
                 string_agg(sp_id, ',' ORDER BY lev,spname) AS sp,
                 (lev::text)||':'||string_agg(spname, ',' ORDER BY lev,spname) AS spname
          FROM t_lev
          GROUP BY table_name,lev
          ORDER BY lev
       )
       LOOP
             ctmp1 := ctmp1 || regexp_replace(c1.sp,',\s*$','') || chr(10);
             ctmp2 := ctmp2 || regexp_replace(c1.spname,',\s*$','') || chr(10);
       END LOOP;
       o_return := ctmp2||CASE WHEN ctmp1 IS NOT NULL AND ctmp1 <> '' THEN chr(10)||'下一步执行的sp列表:'||chr(10) ELSE '' END||ctmp1;

   -- 报券系统标记
   ELSIF i_kind = 'bqxt_flag' THEN
      FOR c1 IN (
        SELECT fn_imp_param_replace(replace(substring(dest_tablename from '/[^/.]+'),'/','')||chr(9)||'${TD}'||chr(9)||'1'||chr(9)||'${NOW}', i_value1) AS flag
          FROM tb_imp_ds2_tbls
         WHERE ds_id = i_sp_id
      )
      LOOP
          o_return := coalesce(o_return,'') || c1.flag || chr(10);
      END LOOP;
      o_return := regexp_replace(o_return,chr(10)||'$','');

   -- 更新 hadoop 表结构（建库、建表、变更）
   ELSIF i_kind = 'updt_hive' THEN
      SELECT string_agg(create_db, chr(10)) || chr(10)
             INTO o_return
        FROM vw_imp_system t
        JOIN (SELECT sysid FROM vw_imp_etl WHERE bcreate = 'Y' AND bupdate = 'n' AND bvalid = 1 GROUP BY sysid) a
          ON a.sysid = t.sysid
       WHERE t.sys_kind = 'etl' AND t.bvalid = 1
         AND t.sysid NOT IN (SELECT fid FROM tb_imp_flag WHERE tradedate = getltd() AND fval = 4 AND kind = 'ETL_END');

      o_return := coalesce(o_return,'') || chr(10);

      FOR tbl IN (
        SELECT tid, lower(dest) AS tblname
          FROM vw_imp_etl
         WHERE bcreate = 'Y' AND bupdate = 'n' AND bvalid = 1
           AND tid IN(SELECT tid FROM tb_imp_tbl_sou GROUP BY tid)
      )
      LOOP
          ctmp1 := 'create external table if not exists `' || replace(tbl.tblname,'.','`.`') || '`(';
          FOR col IN(
            SELECT column_name, dest_type_full AS column_type, col_comment AS column_comment, column_id
              FROM tb_imp_tbl_sou t
             WHERE tid = tbl.tid
            UNION ALL
            SELECT entry_value, entry_content, remark, 10000+row_number() OVER(ORDER BY entry_value)
              FROM tb_dictionary
             WHERE entry_code='2015'
             ORDER BY column_id
          )
          LOOP
              ctmp1 := ctmp1 || chr(10) || '  `' || lower(col.column_name) || '` ' || col.column_type ||
                       CASE WHEN coalesce(col.column_comment,' ')<>' ' THEN ' comment "' || col.column_comment || '"' ELSE '' END || ',';
          END LOOP;
          ctmp1 := regexp_replace(ctmp1 , ',\s*$' , ')');

          SELECT max(tbl_comment) INTO strtmp1 FROM tb_imp_tbl_sou WHERE tid = tbl.tid AND tbl_comment IS NOT NULL;
          IF strtmp1 IS NOT NULL THEN ctmp1 := ctmp1 || ' comment "'|| strtmp1 ||'"'; END IF;

          ctmp1 := ctmp1 || chr(10) || 'partitioned by(logdate string) stored as orc location ''/ods/'||replace(tbl.tblname,'.','/')||''' tblproperties(''orc.compress''=''lz4'');';
          o_return := o_return || ctmp1 || chr(10);
      END LOOP;

      FOR c1 IN (
        SELECT t.alter_sql
          FROM vw_imp_tbl_diff_hive t
          JOIN vw_imp_etl a ON a.bcreate = 'N' AND a.bupdate = 'n' AND a.tid=t.tid
      )
      LOOP
           o_return := o_return || c1.alter_sql || chr(10);
      END LOOP;

   -- 更新 MySQL 表结构
   ELSIF i_kind = 'updt_mysql' THEN
      FOR c1 IN (SELECT alter_sql FROM vw_imp_tbl_diff_mysql)
      LOOP
           o_return := coalesce(o_return,'') || c1.alter_sql || chr(10);
      END LOOP;

   --------------------------------------------
   ----------- 预览相关 -------------
   --------------------------------------------
   ELSIF i_kind = 'preview_etl' THEN
      SELECT string_agg(entry_value,'|' ORDER BY entry_value)
             INTO strtmp1
      FROM tb_dictionary
      WHERE entry_code='2020';

      FOR c1 IN(
        SELECT 'select cast(now() as varchar)||'':预览表'||a.db_name||'.'||a.tbl_name||''';'||
               'insert into stage_hive.'||a.db_name||'.'||a.tbl_name||
               ' select '||
               string_agg(
                 CASE WHEN a.col_type='string' AND upper(a.col_name) ~ ('^('||strtmp1||')$')
                      THEN 'substr("'||a.col_name||'",1,1)||''***'''
                      ELSE '"'||a.col_name||'"' END
               , ',' ORDER BY a.col_idx) ||
               ' from '||a.db_name||'.'||a.tbl_name||
               ' where logdate=''' || fn_imp_value('dest_part_value', t.tid) || ''';' AS hsql
          FROM vw_imp_etl t
          JOIN tb_imp_etl_tbls a ON a.db_name=lower(t.dest_owner) AND a.tbl_name=lower(t.dest_tablename)
         WHERE t.bpreview='Y' AND t.bvalid=1 AND t.sysid = i_sp_id
         GROUP BY a.db_name,a.tbl_name,t.tid
         ORDER BY a.tbl_name
      )
      LOOP
        o_return := coalesce(o_return,'') || c1.hsql || chr(10);
      END LOOP;

   -- 预览前置 SQL
   ELSIF i_kind = 'preview_presql' THEN
      SELECT t.create_db INTO o_return
        FROM vw_imp_system t
       WHERE t.sys_kind='etl' AND t.sysid = i_sp_id;

      FOR c1 IN(
        SELECT 'drop table if exists '||a.db_name||'.'||a.tbl_name||';'||
               'create external table if not exists '||a.db_name||'.'||a.tbl_name||' ( '||
               string_agg('`'||a.col_name||'` '||a.col_type, ', ' ORDER BY a.col_idx) || ') '||
               'partitioned by(logdate string) stored as orc location '''||replace(a.tbl_location,'hdfs://lczq','')||''' tblproperties("external.table.purge"="true");'||
               'msck repair table '||a.db_name||'.'||a.tbl_name||';'||
               'alter table ' || a.db_name||'.'||a.tbl_name ||' drop if exists partition(logdate=' ||fn_imp_value('dest_part_value', t.tid) || ');' ||
               'alter table ' || a.db_name||'.'||a.tbl_name ||' add if not exists partition(logdate=' ||fn_imp_value('dest_part_value', t.tid) || ');' ||
               'alter table ' || a.db_name||'.'||a.tbl_name ||' set tblproperties("external.table.purge"="false");' AS hsql
          FROM vw_imp_etl t
          JOIN tb_imp_etl_tbls a ON a.db_name=lower(t.dest_owner) AND a.tbl_name=lower(t.dest_tablename) AND a.col_idx<1000
         WHERE bpreview='Y' AND t.bvalid=1 AND t.sysid = i_sp_id
         GROUP BY a.db_name,a.tbl_name,a.tbl_location,t.tid
         ORDER BY a.tbl_name
      )
      LOOP
         o_return := coalesce(o_return,'') || chr(10) || c1.hsql;
      END LOOP;
   END IF;
   RETURN o_return;
END;
$$;