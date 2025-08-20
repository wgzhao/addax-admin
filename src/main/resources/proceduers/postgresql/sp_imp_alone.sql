CREATE OR REPLACE FUNCTION sp_imp_alone(
  i_kind   text,
  i_sp_id  text DEFAULT NULL,
  i_value1 text DEFAULT NULL
) RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_tradedate integer := gettd();
  v_remark    text := '';
  v_err       integer := 0;
  v_sp_id     text := i_sp_id;
  v_curtime   timestamp := now();
  strtmp      text;
  c1          record;  -- 显式声明循环记录变量，修复“循环变量必须是记录变量”错误
BEGIN
  -- plan_start
  IF i_kind = 'plan_start' THEN
    FOR c1 IN (
      SELECT sysid FROM vw_imp_system
       WHERE sys_kind='etl' AND bvalid=1 AND fn_imp_pntype(db_start_type)=1
         AND to_char(v_curtime,'HH24MI') NOT BETWEEN '0001' AND '0023'
         AND fn_imp_timechk(v_curtime, db_start)=1 AND i_sp_id IS NULL
      UNION ALL
      SELECT sysid FROM vw_imp_system WHERE sys_kind='etl' AND sysid=i_sp_id AND i_sp_id IS NOT NULL
      UNION ALL
      SELECT fid FROM tb_imp_flag WHERE tradedate=v_tradedate AND kind='ETL_START' AND fval=2 AND i_sp_id IS NULL
    ) LOOP
      UPDATE tb_imp_etl
         SET flag = CASE WHEN sou_sysid='ZD' AND v_tradedate<>getparam('CW1')::int AND sou_tablename LIKE 'qt%'
                         THEN 'X' ELSE 'N' END,
             runtime = runtime * POWER(2::numeric, 3-retry_cnt),
             retry_cnt=3,
             etl_kind='A'
       WHERE flag NOT IN ('R','W') AND (flag<>'X' OR sou_sysid='ZD') AND sou_sysid=c1.sysid;

      SELECT CASE WHEN COUNT(1)>0 THEN chr(10)||'警告:存在'||COUNT(1)||'个正在采集的任务!!['||substring(string_agg(sou_tablename,','),1,350)||']' END
        INTO v_remark FROM tb_imp_etl WHERE flag='R' AND sou_sysid=c1.sysid;

      SELECT CASE WHEN COUNT(1)>0 THEN chr(10)||'重复执行:上一次'||
             CASE MAX(fval) WHEN 4 THEN '成功' WHEN 5 THEN '失败' ELSE '未结束' END||',时间['||
             MAX(CASE WHEN fval=3 THEN to_char(dw_clt_date,'YYYY-MM-DD HH24:MI:SS') END)||'=>'||
             MAX(CASE WHEN fval<>3 THEN to_char(dw_clt_date,'HH24:MI:SS') END)||']' END
        INTO strtmp FROM tb_imp_flag
       WHERE tradedate=v_tradedate AND kind IN('ETL_START','ETL_END') AND fid=c1.sysid AND fval IN(3,4,5);
      v_remark := coalesce(v_remark,'')||coalesce(strtmp,'');

      SELECT CASE WHEN v_remark IS NOT NULL OR COUNT(1)=1 THEN '110' ELSE '000' END
        INTO strtmp FROM vw_imp_etl_judge WHERE px=1 AND sysid=c1.sysid;
      PERFORM sp_sms('数据源['||coalesce(fn_imp_value('sysname',c1.sysid),c1.sysid)||']正式开始采集!!'||v_remark,'1',strtmp);

      PERFORM sp_imp_flag('del','ETL_START,ETL_END,TASK_GROUP', c1.sysid, 0);
      PERFORM sp_imp_flag('add','ETL_START', c1.sysid, 3);
      v_sp_id := coalesce(v_sp_id,'')||c1.sysid||',';
    END LOOP;

    -- 定时触发 ODS/DS/SP
    UPDATE tb_imp_etl SET flag='N', retry_cnt=3, etl_kind='A'
     WHERE flag NOT IN('R','X') AND fn_imp_timechk(v_curtime, after_retry_fixed)=1 AND fn_imp_pntype(after_retry_pntype)=1;
    UPDATE tb_imp_ds2 SET flag='N', retry_cnt=3
     WHERE flag NOT IN('R','X') AND fn_imp_timechk(v_curtime, pn_fixed, pn_interval, pn_range)=1 AND fn_imp_pntype(pn_type)=1
       AND (fn_imp_freqchk(run_freq)=1 OR run_freq='D');
    UPDATE tb_imp_sp SET flag='N', retry_cnt=3
     WHERE flag NOT IN('R','X') AND fn_imp_timechk(v_curtime, pn_fixed, pn_interval, pn_range)=1 AND fn_imp_pntype(pn_type)=1
       AND (fn_imp_freqchk(run_freq)=1 OR run_freq='D');

    IF to_char(v_curtime,'HH24MI') BETWEEN '0800' AND '2300' AND to_char(v_curtime,'YYYYMMDD') IN (gettd()::text, getntd()::text) THEN
      UPDATE tb_imp_etl t SET flag='N', retry_cnt=3, etl_kind='R'
       WHERE (
         fn_imp_timechk(v_curtime,realtime_fixed,realtime_interval,realtime_interval_range)=1 OR
         (realtime_taskgroup IS NOT NULL AND position(','||coalesce(i_sp_id,'0')||',' IN ','||realtime_taskgroup||',')>0)
       ) AND flag IN('E','Y','N');

      DELETE FROM tb_imp_flag
       WHERE tradedate=v_tradedate AND kind='TASK_GROUP' AND fid IN (
         SELECT a.task_group FROM tb_imp_etl t
         JOIN vw_imp_taskgroup a ON position(','||a.task_group||',' IN ','||t.realtime_taskgroup||',')>0
         WHERE t.realtime_taskgroup IS NOT NULL AND t.flag<>'X'
         GROUP BY a.task_group HAVING COUNT(1)=SUM(CASE WHEN etl_kind='R' AND flag='N' THEN 1 ELSE 0 END)
       );
    END IF;

  ELSIF i_kind = 'sp_start' THEN
    FOR c1 IN (
      SELECT a.task_group FROM (
        SELECT task_group FROM vw_imp_taskgroup_over
        UNION ALL
        SELECT task_group FROM vw_imp_ds2_needs WHERE bmulti=1 GROUP BY task_group HAVING SUM(bover)=COUNT(1)
      ) a LEFT JOIN (SELECT fid FROM tb_imp_flag WHERE tradedate=v_tradedate AND kind='TASK_GROUP' GROUP BY fid) b
        ON b.fid=a.task_group WHERE b.fid IS NULL GROUP BY a.task_group
    ) LOOP
      UPDATE tb_imp_ds2 SET flag='N', retry_cnt=3 WHERE task_group=c1.task_group AND flag IN('Y','E') AND fn_imp_freqchk(run_freq)=1;
      UPDATE tb_imp_sp  SET flag='N', retry_cnt=3, runtime=runtime*POWER(2::numeric,3-retry_cnt)
       WHERE realtime_taskgroup=c1.task_group AND flag IN('Y','E') AND fn_imp_freqchk(run_freq)=1;
      INSERT INTO tb_imp_flag(tradedate,kind,fid,fval) VALUES (v_tradedate,'TASK_GROUP',c1.task_group,1);
      v_sp_id := coalesce(v_sp_id,'')||c1.task_group||',';
    END LOOP;

    UPDATE tb_imp_sp SET flag='N', retry_cnt=3, runtime=runtime*POWER(2::numeric,3-retry_cnt)
     WHERE flag<>'R' AND sp_id IN (
       SELECT sp_id FROM vw_imp_sp_needs GROUP BY sp_id
        HAVING COUNT(1)=SUM(CASE WHEN needs_flag='Y' THEN 1 ELSE 0 END)
           AND SUM(CASE WHEN needs_end_time>COALESCE(start_time, needs_end_time - interval '1 second') THEN 1 ELSE 0 END)>0
     );
    UPDATE tb_imp_sp SET flag='Y'
     WHERE sp_id IN (SELECT sp_id FROM vw_imp_sp_needs WHERE flag='N' AND needs_flag='N'
                     UNION ALL SELECT needs FROM vw_imp_sp_needs WHERE flag='R' AND needs_flag='N');
    DELETE FROM tb_imp_flag WHERE tradedate=v_tradedate AND kind='TASK_GROUP'
      AND fid IN (SELECT task_group FROM tb_imp_sp WHERE flag='N' AND task_group IS NOT NULL GROUP BY task_group);

  ELSIF i_kind = 'etl_end' THEN
    WITH s AS (
      SELECT SUM(CASE WHEN flag='E' THEN 1 ELSE 0 END) nerr,
             SUM(CASE WHEN flag='Y' THEN 1 ELSE 0 END) nok,
             COUNT(1) ncnt,
             FLOOR(EXTRACT(EPOCH FROM (MAX(end_time)-MIN(start_time))))::bigint runtime,
             string_agg(CASE WHEN flag='E' THEN dest_tablename END, ',') err_tbls
        FROM vw_imp_etl WHERE sysid=i_sp_id AND bvalid=1
    ) SELECT '总数:'||ncnt||'张==>{成功:'||nok||'张'||CASE WHEN nerr>0 THEN ',错误:'||nerr||'张' ELSE '' END||'}'||chr(10)||
             '耗时:'||runtime||'秒'||CASE WHEN nerr>0 THEN chr(10)||'错误的表:{'||substring(coalesce(err_tbls,''),1,300)||'}' ELSE '' END,
             COALESCE(nerr,0)
      INTO v_remark, v_err FROM s;

    SELECT CASE WHEN v_err>0 OR COUNT(1)=1 THEN '110' ELSE '000' END INTO strtmp FROM vw_imp_etl_judge WHERE px=1 AND sysid=i_sp_id;
    PERFORM sp_sms('数据源['||coalesce(fn_imp_value('sysname',i_sp_id),i_sp_id)||']采集结束'||chr(10)||v_remark,'UF',strtmp);
    INSERT INTO tb_imp_flag(tradedate,kind,fid,fval) VALUES (v_tradedate,'ETL_END',i_sp_id, CASE WHEN v_err=0 THEN 4 ELSE 5 END);

    INSERT INTO tb_imp_flag(tradedate,kind,fid,fval)
    SELECT v_tradedate,'TASK_GROUP',a.task_group,1
      FROM tb_imp_etl t JOIN vw_imp_taskgroup a ON position(','||a.task_group||',' IN ','||t.realtime_taskgroup||',')>0
      LEFT JOIN tb_imp_flag b ON b.tradedate=v_tradedate AND b.kind='TASK_GROUP' AND b.fid=a.task_group
     WHERE t.realtime_taskgroup IS NOT NULL AND t.sou_sysid=i_sp_id AND b.fid IS NULL GROUP BY a.task_group;

  ELSIF i_kind = 'colexch_updt' THEN
    INSERT INTO tmp_imp(pkid) SELECT tid FROM vw_imp_etl WHERE bupdate='n';
    SELECT COUNT(1) INTO v_err FROM tmp_imp;
    DELETE FROM tb_imp_tbl_hdp WHERE tid IN(SELECT pkid FROM tmp_imp) OR v_err=0 OR tid NOT IN(SELECT tid FROM tb_imp_etl);
    INSERT INTO tb_imp_tbl_hdp(tid,hive_owner,hive_tablename,col_name,col_type_full,col_type,col_precision,col_scale,col_idx,tbl_comment,col_comment,cd_id)
    SELECT tid,hive_owner,hive_tablename,col_name,col_type_full,col_type,col_precision,col_scale,col_idx,tbl_comment,col_comment,cd_id FROM vw_imp_tbl_hdp WHERE bupdate='n' OR v_err=0;
    DELETE FROM tb_imp_tbl_sou WHERE tid IN(SELECT pkid FROM tmp_imp) OR v_err=0 OR tid NOT IN(SELECT tid FROM tb_imp_etl);
    INSERT INTO tb_imp_tbl_sou(tid,sou_db_conn,sou_owner,sou_tablename,column_name_orig,column_name,column_id,data_type,data_length,data_precision,data_scale,tbl_comment,col_comment,dest_type,dest_type_full)
    SELECT tid,sou_db_conn,sou_owner,sou_tablename,column_name_orig,column_name,column_id,data_type,data_length,data_precision,data_scale,tbl_comment,col_comment,dest_type,dest_type_full FROM vw_imp_tbl_sou WHERE bupdate='n' OR v_err=0;

  ELSIF i_kind = 'real_after' THEN
    -- 实时与盘后关键时间点任务处理
    UPDATE tb_imp_etl SET bupdate='Y', etl_kind=i_sp_id WHERE realtime_sou_owner IS NOT NULL;
    UPDATE tb_imp_etl SET bupdate='Y'
     WHERE i_sp_id = 'R' AND tid IN (
       SELECT tid FROM vw_imp_tbl_diff_hive
       UNION ALL
       SELECT tid FROM vw_imp_tbl_diff_mysql
     );
    FOR c1 IN (
      SELECT '采集模式置为' || i_sp_id || chr(10) || string_agg(sms, chr(10)) AS sms
        FROM (
              SELECT sou_sysid || ':' || string_agg(dest_tablename || chr(10), '' ORDER BY dest_tablename) AS sms
                FROM tb_imp_etl
               WHERE bupdate='Y' AND etl_kind=i_sp_id
               GROUP BY sou_sysid
             ) t
      UNION ALL
      SELECT coalesce(fn_imp_value('taskname', tid), tid::text) || '的表有' || COUNT(1) || '个字段类型变更!!!'
        FROM vw_imp_tbl_diff_hive
       WHERE substring(alter_sql FROM 1 FOR 300) LIKE '%` change `%'
       GROUP BY tid
    ) LOOP
      PERFORM sp_sms(substring(c1.sms,1,400),'1','110');
    END LOOP;

  ELSIF i_kind = 'bupdate' THEN
    -- 数据源刷新表结构：Y/n/etl_err/N/D
    IF i_sp_id = 'etl_err' THEN
      FOR c1 IN (
        WITH t_errsys AS (
          SELECT fid, fval, row_number() OVER(PARTITION BY fid ORDER BY dw_clt_date DESC) AS px
            FROM tb_imp_flag
           WHERE tradedate = v_tradedate AND kind = 'ETL_END'
        )
        SELECT fid FROM t_errsys WHERE px = 1 AND fval = 5
      ) LOOP
        UPDATE tb_imp_etl
           SET bupdate = 'Y', flag = 'W', retry_cnt = 3
         WHERE flag = 'E' AND sou_sysid = c1.fid;
        PERFORM sp_sms(coalesce(fn_imp_value('sysname',c1.fid), c1.fid) || '采集失败,尝试自动修复','1','110');
      END LOOP;

    ELSIF i_sp_id = 'N' THEN
      -- 建表完成置为结束
      UPDATE tb_imp_etl t
         SET bcreate = 'N'
        FROM vw_imp_etl v
       WHERE v.bcreate='Y' AND v.bupdate='n' AND v.bvalid=1 AND t.tid=v.tid
         AND t.tid IN (SELECT tid FROM tb_imp_tbl_hdp GROUP BY tid);
      -- 删除自动生成的命令
      DELETE FROM tb_imp_sp_com
       WHERE com_idx IN (99,100)
         AND sp_id IN (SELECT tid FROM tb_imp_etl WHERE bupdate='n');
      -- 99: 分区语句
      INSERT INTO tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
      SELECT tid,99,'hive',
             'alter table '||replace(lower(dest),'.','.`')||'` drop if exists partition(logdate=''${dest_part}'');'||chr(10)||
             'alter table '||replace(lower(dest),'.','.`')||'` add if not exists partition(logdate=''${dest_part}'');'
        FROM vw_imp_etl
       WHERE bupdate='n';
      -- 100: datax 采集 json
      INSERT INTO tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
      SELECT tid,100,'addax', fn_imp_value('jobfile',tid)
        FROM tb_imp_etl
       WHERE bupdate='n';
      -- 更新结束，状态回收
      UPDATE tb_imp_etl t
         SET bupdate='N',
             flag    = CASE WHEN flag='W' AND bcreate='N' THEN 'N' ELSE flag END,
             retry_cnt = CASE WHEN flag='W' AND bcreate='N' THEN 1 ELSE retry_cnt END
        FROM vw_imp_etl v
       WHERE v.bupdate='n' AND t.tid=v.tid;

    ELSIF i_sp_id = 'D' THEN
      -- 数据服务更新涉及表（按中间视图汇���登记）
      IF i_value1 = 'all' THEN
        DELETE FROM tb_imp_sp_needtab WHERE kind='DS';
      ELSE
        DELETE FROM tb_imp_sp_needtab WHERE kind='DS' AND sp_id = i_value1;
      END IF;
      INSERT INTO tb_imp_sp_needtab(sp_id,table_name,kind)
      SELECT DISTINCT ds_id, upper(sou_owner||'.'||sou_tablename), 'DS'
        FROM vw_imp_ds2_mid
       WHERE bvalid=1 AND (ds_id = i_value1 OR i_value1='all') AND sou_ishdp=1 AND sou_allsql=0
      ON CONFLICT DO NOTHING;

    ELSE
      -- 标志切换 Y/n 按 sou_db_conn
      IF i_value1 = 'Y' THEN
        UPDATE tb_imp_etl t
           SET bupdate='Y'
          FROM vw_imp_etl v
         WHERE v.sou_db_conn = i_sp_id AND v.bupdate <> 'Y' AND t.tid=v.tid;
      ELSIF i_value1 = 'n' THEN
        UPDATE tb_imp_etl t
           SET bupdate='n'
          FROM vw_imp_etl v
         WHERE v.sou_db_conn = i_sp_id AND v.bupdate = 'Y' AND t.tid=v.tid;
      END IF;
    END IF;

  ELSIF i_kind = 'get_hdptbls' THEN
    -- 获取最新的 hadoop 表结构(不含视图)
    INSERT INTO tmp_imp(pkid)
    SELECT db_name||'.'||tbl_name
      FROM (
            SELECT * FROM tb_imp_etl_tbls_tmp
            EXCEPT
            SELECT * FROM tb_imp_etl_tbls
           ) t
     GROUP BY db_name||'.'||tbl_name;

    DELETE FROM tb_imp_etl_tbls WHERE (db_name||'.'||tbl_name) IN (SELECT pkid FROM tmp_imp);
    INSERT INTO tb_imp_etl_tbls
    SELECT * FROM tb_imp_etl_tbls_tmp WHERE (db_name||'.'||tbl_name) IN (SELECT pkid FROM tmp_imp);

  ELSE
    -- 未知分支，忽略
    NULL;
  END IF;

  INSERT INTO tb_imp_jour(kind,trade_date,status,key_id,remark)
  VALUES ('public', v_tradedate, i_kind, v_sp_id,
          coalesce(v_remark,'')||chr(10)||'开始时间：'||to_char(v_curtime,'YYYYMMDD HH24:MI:SS')||
          ',执行耗时：'||FLOOR(EXTRACT(EPOCH FROM (now()-v_curtime)))::text||
          '秒==>传入参数：{i_kind=['||coalesce(i_kind,'')||'],i_sp_id=['||coalesce(i_sp_id,'')||'],i_value1=['||coalesce(i_value1,'')||'],v_err=['||coalesce(v_err,0)||']}<==');

  RETURN;
EXCEPTION WHEN OTHERS THEN
  PERFORM sp_sms('sp_imp_alone执行报错,kind=['||coalesce(i_kind,'')||'],sp_id=['||coalesce(i_sp_id,'')||'],i_value1=['||coalesce(i_value1,'')||'],错误说明=['||SQLERRM||']','18692206867','110');
  RAISE;
END;
$$;
