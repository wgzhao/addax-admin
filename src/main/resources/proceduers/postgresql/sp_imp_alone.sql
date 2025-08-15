-- PostgreSQL version of sp_imp_alone
CREATE OR REPLACE FUNCTION sp_imp_alone(i_kind varchar, i_sp_id varchar DEFAULT '', i_value1 varchar DEFAULT '')
RETURNS void AS $$
DECLARE
   v_tradedate integer;
   v_remark text;
   v_err integer;
   v_sp_id text;
   strtmp text;
   ctmp text;
   v_curtime timestamp;
   c1 record;
   c2 record;
   ds record;
   inf record;
BEGIN
   v_sp_id := i_sp_id;
   v_remark := '';
   v_tradedate := gettd();
   v_curtime := CURRENT_TIMESTAMP;

   -- 符合条件判断开始
   IF i_kind = 'plan_start' THEN
      -- 盘后采集
      FOR c1 IN (
                 -- 盘后定时采集
                 SELECT sysid FROM vw_imp_system
                  WHERE sys_kind='etl'
                    AND bvalid=1
                    AND fn_imp_pntype(db_start_type)=1
                    AND to_char(v_curtime, 'HH24MI') NOT BETWEEN '0001' AND '0023'
                    AND fn_imp_timechk(v_curtime,db_start) = 1
                    AND i_sp_id IS NULL
                  UNION ALL
                 -- 手工指定数据源采集
                 SELECT sysid FROM vw_imp_system
                  WHERE sys_kind = 'etl' AND sysid = i_sp_id AND i_sp_id IS NOT NULL
                  UNION ALL
                 -- 已经完成采集前初始化的判断标志数据源
                 SELECT fid FROM tb_imp_flag
                  WHERE tradedate = v_tradedate AND kind = 'ETL_START' AND fval = 2 AND i_sp_id IS NULL
      ) LOOP
          -- 数据源采集任务状态置为N
          UPDATE tb_imp_etl
             SET flag      = (CASE WHEN sou_sysid='ZD' AND v_tradedate<>getparam('CW1') AND sou_tablename LIKE 'qt%' THEN 'X' ELSE 'N' END),
                 runtime   = runtime*power(2,3-retry_cnt),
                 retry_cnt = 3,
                 etl_kind  = 'A'
           WHERE flag NOT IN('R','W')
             AND (flag <> 'X' OR sou_sysid = 'ZD')
             AND sou_sysid = c1.sysid;

          -- 检查数据源下是否有正在执行的采集
          SELECT CASE WHEN count(1)>0 THEN
                      E'\n' || '警告:存在'|| count(1) ||'个正在采集的任务!![' || to_char(substring(string_agg(sou_tablename, ',') FROM 1 FOR 350)) || ']' END
                 INTO v_remark
            FROM tb_imp_etl
           WHERE flag = 'R' AND sou_sysid = c1.sysid;

          -- 检查数据源是否重复执行
          SELECT CASE WHEN count(1)>0 THEN
                      E'\n' || '重复执行:上一次'||CASE max(fval) WHEN 4 THEN '成功' WHEN 5 THEN '失败' ELSE '未结束' END
                              || ',时间['||max(CASE WHEN fval=3 THEN to_char(dw_clt_date,'YYYY-MM-DD HH24:MI:SS') END)||
                                 '=>'||max(CASE WHEN fval<>3 THEN to_char(dw_clt_date,'HH24:MI:SS') END)||']' END
                 INTO strtmp
            FROM tb_imp_flag
           WHERE tradedate = v_tradedate AND kind IN('ETL_START','ETL_END') AND fid = c1.sysid AND fval IN(3,4,5);
          v_remark := v_remark || strtmp;

          -- 数据源开始采集提醒(判断标志,重复执行,正在执行 的数据源需要短信提醒)
          SELECT CASE WHEN v_remark IS NOT NULL OR count(1)=1 THEN '110' ELSE '000' END
                 INTO strtmp
            FROM vw_imp_etl_judge
           WHERE px = 1 AND sysid = c1.sysid;
          PERFORM sp_sms('数据源[' || to_char(fn_imp_value('sysname',c1.sysid)) || ']正式开始采集!!' || v_remark, '1', strtmp);

          -- 数据源标志处理:只保留开始标志
          PERFORM sp_imp_flag('del','ETL_START,ETL_END,TASK_GROUP',c1.sysid);
          PERFORM sp_imp_flag('add','ETL_START',c1.sysid,3);

          v_sp_id := v_sp_id || c1.sysid || ',';
      END LOOP;

      -- 判断标志符合采集条件,可以执行采集前置任务
      FOR c1 IN(SELECT sysid,sys_name
                FROM vw_imp_etl_judge
                WHERE bstart=-1 AND px<=2 AND i_sp_id IS NULL
                GROUP BY sysid,sys_name
                HAVING max(CASE WHEN px=1 AND fval=1 THEN 1 ELSE 0 END)=1 AND
                       min(CASE WHEN px=2 AND fval=1 THEN 0 ELSE 1 END)=1)
      LOOP
        PERFORM sp_imp_flag('add','ETL_START',c1.sysid,0);
        PERFORM sp_sms('判断标志的数据源[' || c1.sysid||'_'||c1.sys_name || ']已经符合采集条件');
        v_sp_id := v_sp_id || c1.sysid || ',';
      END LOOP;

      -- 无前置,按计划调起的ODS采集,SP计算及数据服务
      -- 盘后任务重新采集
      UPDATE tb_imp_etl
         SET flag='N',retry_cnt=3,etl_kind='A'
       WHERE flag NOT IN('R','X')
         AND fn_imp_timechk(v_curtime, after_retry_fixed) = 1
         AND fn_imp_pntype(after_retry_pntype) = 1;
      -- 计划调起数据服务
      UPDATE tb_imp_ds2
         SET flag='N',retry_cnt=3
       WHERE flag NOT IN('R','X')
         AND fn_imp_timechk(v_curtime, pn_fixed, pn_interval, pn_range) = 1
         AND fn_imp_pntype(pn_type) = 1
         AND (fn_imp_freqchk(run_freq) = 1 OR run_freq = 'D');
      -- 计划调起SP计算
      UPDATE tb_imp_sp
         SET flag='N',retry_cnt=3
       WHERE flag NOT IN('R','X')
         AND fn_imp_timechk(v_curtime, pn_fixed, pn_interval, pn_range) = 1
         AND fn_imp_pntype(pn_type) = 1
         AND (fn_imp_freqchk(run_freq) = 1 OR run_freq = 'D');

      -- 实时采集:交易日当天8:00-23:00,或传入的指定任务组
      IF to_char(v_curtime, 'HH24MI') BETWEEN '0800' AND '2300' AND to_char(v_curtime, 'YYYYMMDD') IN (gettd()::text, getntd()::text) THEN
        UPDATE tb_imp_etl t
           SET flag = 'N', retry_cnt = 3, etl_kind = 'R'
         WHERE (
               fn_imp_timechk(v_curtime,realtime_fixed,realtime_interval,realtime_interval_range) = 1 OR
               (realtime_taskgroup IS NOT NULL AND position(','||COALESCE(i_sp_id,'0')||',' IN ','||realtime_taskgroup||',')>0)
               )
           AND flag IN ('E', 'Y', 'N');

        -- 删除符合本次实时采集表的任务组完成标志
        DELETE FROM tb_imp_flag
         WHERE tradedate = v_tradedate
           AND kind = 'TASK_GROUP'
           AND fid IN (SELECT a.task_group FROM tb_imp_etl t
                        INNER JOIN vw_imp_taskgroup a
                           ON position(','||a.task_group||',' IN ','||t.realtime_taskgroup||',')>0
                        WHERE t.realtime_taskgroup IS NOT NULL AND t.flag <> 'X'
                        GROUP BY a.task_group
                       HAVING count(1) = sum(CASE WHEN etl_kind = 'R' AND flag = 'N' THEN 1 ELSE 0 END));
      END IF;

   -- 符合条件的任务开始执行
   ELSIF i_kind = 'sp_start' THEN
      -- 符合执行条件的任务组
       FOR c1 IN(SELECT a.task_group
                FROM (
                  SELECT task_group FROM vw_imp_taskgroup_over  -- 常规任务组
                  UNION ALL
                  SELECT task_group FROM vw_imp_ds2_needs WHERE bmulti=1 GROUP BY task_group HAVING sum(bover)=count(1) -- 组合任务组（数据源+任务组的模式）
                ) a
                  -- 剔除已经执行过的任务组
                  LEFT JOIN (SELECT fid
                               FROM tb_imp_flag
                              WHERE tradedate = v_tradedate
                                AND kind = 'TASK_GROUP'
                              GROUP BY fid) b
                    ON b.fid = a.task_group
                 WHERE b.fid IS NULL
                 GROUP BY a.task_group)
      LOOP
        -- 数据服务
        UPDATE tb_imp_ds2 SET flag = 'N', retry_cnt = 3
         WHERE task_group = c1.task_group AND flag IN('Y','E') AND fn_imp_freqchk(run_freq)=1;
        -- SP计算
        UPDATE tb_imp_sp SET flag = 'N', retry_cnt = 3, runtime = runtime * power(2,3-retry_cnt)
         WHERE realtime_taskgroup = c1.task_group AND flag IN('Y','E') AND fn_imp_freqchk(run_freq)=1;
        -- 写任务组完成标志
        INSERT INTO tb_imp_flag(tradedate,kind,fid,fval) VALUES (v_tradedate, 'TASK_GROUP', c1.task_group, 1);
        v_sp_id := v_sp_id || c1.task_group || ',';
      END LOOP;

      -- 符合执行条件的sp
      UPDATE tb_imp_sp SET flag = 'N', retry_cnt = 3, runtime = runtime * power(2,3-retry_cnt)
      WHERE flag<>'R'
        AND sp_id IN(SELECT sp_id FROM vw_imp_sp_needs GROUP BY sp_id
                     HAVING count(1) = sum(CASE WHEN needs_flag = 'Y' THEN 1 ELSE 0 END)
                        AND sum(CASE WHEN needs_end_time > COALESCE(start_time, needs_end_time - interval '1 second') THEN 1 ELSE 0 END) > 0);
      -- SP及前置SP均为N的，当前SP临时置为Y
      -- 前置SP准备执行，但是下游的SP正在执行，前置SP临时置为Y
      UPDATE tb_imp_sp SET flag='Y'
       WHERE sp_id IN(SELECT sp_id FROM vw_imp_sp_needs WHERE flag='N' AND needs_flag='N'
                      UNION ALL
                      SELECT needs FROM vw_imp_sp_needs WHERE flag='R' AND needs_flag='N');
      -- 重复执行的sp，删除任务组的完成标志
      DELETE FROM tb_imp_flag
       WHERE tradedate = v_tradedate AND kind = 'TASK_GROUP'
         AND position(','||fid||',' IN (SELECT ','||string_agg(task_group, ',')||',' FROM (SELECT task_group FROM tb_imp_sp WHERE flag='N' AND task_group IS NOT NULL GROUP BY task_group) as t1)) > 0;

   -- 数据源采集结束
   ELSIF i_kind = 'etl_end' THEN
      SELECT '总数:'||ncnt||'张==>{成功:'||nok||'张'||
             CASE WHEN nerr>0 THEN ',错误:'||nerr||'张' ELSE '' END ||'}'||E'\n'||
             '耗时:'||runtime||'秒'||
             CASE WHEN nerr>0 THEN E'\n'||'错误的表:{'||to_char(substring(err_tbls FROM 1 FOR 300))||'}' ELSE '' END,
             COALESCE(nerr,0)
             INTO v_remark,v_err
      FROM (
      SELECT sum(CASE WHEN flag='E' THEN 1 ELSE 0 END) nerr,
             sum(CASE WHEN flag='Y' THEN 1 ELSE 0 END) nok,
             count(1) ncnt,
             trunc(extract(epoch from (max(end_time)-min(start_time)))) runtime,
             string_agg(CASE WHEN flag='E' THEN dest_tablename END, ',') err_tbls
      FROM vw_imp_etl
      WHERE sysid = i_sp_id AND bvalid = 1) as ve1;

      SELECT CASE WHEN v_err>0 OR count(1)=1 THEN '110' ELSE '000' END
             INTO strtmp
      FROM vw_imp_etl_judge
      WHERE px = 1 AND sysid = i_sp_id;
      PERFORM sp_sms('数据源[' || to_char(fn_imp_value('sysname',i_sp_id)) || ']采集结束'||E'\n'||v_remark, 'UF', strtmp);
      INSERT INTO tb_imp_flag(tradedate,kind,fid,fval) VALUES (v_tradedate, 'ETL_END', i_sp_id, CASE WHEN v_err = 0 THEN 4 ELSE 5 END);

      -- 防止类型变更为实时采集时，自动调起实时的任务组
      INSERT INTO tb_imp_flag(tradedate,kind,fid,fval)
      SELECT v_tradedate, 'TASK_GROUP', a.task_group, 1
        FROM tb_imp_etl t
       INNER JOIN vw_imp_taskgroup a
          ON position(','||a.task_group||',' IN ','||t.realtime_taskgroup||',') > 0
        LEFT JOIN tb_imp_flag b
          ON b.tradedate = v_tradedate
         AND b.kind = 'TASK_GROUP'
         AND b.fid = a.task_group
       WHERE t.realtime_taskgroup IS NOT NULL
         AND t.sou_sysid = i_sp_id
         AND b.fid IS NULL
       GROUP BY a.task_group;

   -- 实时与盘后的两个关键时间点任务处理
   ELSIF i_kind = 'real_after' THEN
      -- 实时与盘后采集的用户不一致时,采集前先刷新表结构信息,20210315修改为不关注flag字段
      UPDATE tb_imp_etl SET bupdate='Y',etl_kind=i_sp_id
       WHERE realtime_sou_owner IS NOT NULL;
      -- 有字段变更的,更新表(在调用该任务前,必须调用一次全量的获取表结构)(目前仅在交易日早上更新一次表结构)
      UPDATE tb_imp_etl SET bupdate='Y'
       WHERE i_sp_id = 'R' AND tid IN(SELECT tid FROM vw_imp_tbl_diff_hive UNION SELECT tid FROM vw_imp_tbl_diff_mysql);

      FOR c1 IN(SELECT '采集模式置为' || i_sp_id || E'\n' || replace(string_agg(sms, ','), ',', E'\n') sms
                  FROM (SELECT sou_sysid || ':' || string_agg(dest_tablename || E'\n', '') sms
                          FROM tb_imp_etl
                         WHERE bupdate = 'Y' AND etl_kind = i_sp_id
                         GROUP BY sou_sysid) as te1
                 UNION ALL
                SELECT fn_imp_value('taskname',tid)||'的表有'||count(1)||'个字段类型变更!!!' FROM vw_imp_tbl_diff_hive
                 WHERE substring(alter_sql FROM 1 FOR 300) LIKE '%` change `%' GROUP BY tid)
      LOOP
          PERFORM sp_sms(substring(c1.sms FROM 1 FOR 400),'1','110');
      END LOOP;

   -- 更新与建表的过程处理
   ELSIF i_kind = 'bupdate' THEN
      -- 数据源需要重新刷新表结构:Y
      UPDATE tb_imp_etl SET bupdate='Y'
       WHERE ctid IN (SELECT ctid FROM vw_imp_etl WHERE sou_db_conn = i_sp_id AND bupdate <> 'Y' AND i_value1 = 'Y');

      -- 获取完表结构:n
      UPDATE tb_imp_etl SET bupdate='n'
       WHERE ctid IN (SELECT ctid FROM vw_imp_etl WHERE sou_db_conn = i_sp_id AND bupdate = 'Y' AND i_value1 = 'n');

      -- 数据源采集失败,主动刷新表结构
      IF i_sp_id = 'etl_err' THEN
        FOR c1 IN (WITH t_errsys AS(SELECT fid, fval, row_number() OVER(PARTITION BY fid ORDER BY dw_clt_date DESC) px FROM tb_imp_flag WHERE tradedate = v_tradedate AND kind = 'ETL_END')
                   SELECT fid FROM t_errsys WHERE px = 1 AND fval = '5')
        LOOP
           UPDATE tb_imp_etl
              SET bupdate = 'Y', flag = 'W', retry_cnt = 3
            WHERE flag = 'E' AND sou_sysid = c1.fid;
           PERFORM sp_sms(to_char(fn_imp_value('sysname',c1.fid))||'采集失败,尝试自动修复','1','110');
        END LOOP;

      -- 更新表结构:N updt_json
      ELSIF i_sp_id = 'N' THEN
        -- 建表完成,置结束状态
        UPDATE tb_imp_etl
           SET bcreate = 'N'
         WHERE ctid IN (SELECT ctid FROM vw_imp_etl WHERE bcreate = 'Y' AND bupdate = 'n' AND bvalid = 1 AND tid IN (SELECT tid FROM tb_imp_tbl_hdp GROUP BY tid));

        -- 删除本次更新表涉及的命令列表(99,100为自动生成，其他的idx可以自定义)
        DELETE FROM tb_imp_sp_com
         WHERE com_idx IN(99,100)
           AND sp_id IN(SELECT tid FROM tb_imp_etl WHERE bupdate = 'n');
        -- 99:分区
        INSERT INTO tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
        SELECT tid,99,'hive',
               'alter table '||replace(lower(dest),'.','.`')||'` drop if exists partition(logdate=''${dest_part}'');'||E'\n'||
               'alter table '||replace(lower(dest),'.','.`')||'` add if not exists partition(logdate=''${dest_part}'');'
          FROM vw_imp_etl t
         WHERE bupdate = 'n';
        -- 100:datax采集的json
        INSERT INTO tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
        SELECT tid,100,'addax',fn_imp_value('jobfile',tid)
          FROM tb_imp_etl
         WHERE bupdate = 'n';

        -- 更新结束，状态置为N
        UPDATE tb_imp_etl
           SET bupdate = 'N',
               flag    = CASE WHEN flag='W' AND bcreate='N' THEN 'N' ELSE flag END,
               retry_cnt = CASE WHEN flag='W' AND bcreate='N' THEN 1 ELSE retry_cnt END
         WHERE bupdate = 'n';

      -- 数据服务更新涉及表
      ELSIF i_sp_id = 'D' THEN
        v_remark := to_char(fn_imp_value('get_schema'));
        DELETE FROM tb_imp_sp_needtab WHERE kind='DS' AND i_value1 IN(sp_id,'all');
        FOR ds IN (SELECT ds_id FROM vw_imp_ds2 WHERE bvalid=1 AND (ds_id = i_value1 OR i_value1 = 'all'))
        LOOP
          FOR c1 IN(SELECT regexp_replace(upper(' '||sou_table),'[^A-Z0-9_]{1}(HBASE|CLICKHOUSE|MY_JSC|MY_ZG)\.',' XXX_') coms
                      FROM vw_imp_ds2_mid WHERE ds_id = ds.ds_id AND bvalid = 1 AND sou_ishdp = 1 AND sou_allsql=0)
          LOOP
            ctmp := c1.coms;
            FOR c2 IN 1..500
            LOOP
              strtmp := trim(regexp_substr(ctmp,' ('||v_remark||')\.[A-Z0-9\_]+',1,1));
              IF strtmp IS NOT NULL THEN
                INSERT INTO tb_imp_sp_needtab(sp_id,table_name,kind)
                SELECT ds.ds_id, strtmp, 'DS'
                FROM tb_imp_sp_needtab
                WHERE kind = 'DS' AND table_name = strtmp AND sp_id = ds.ds_id
                HAVING count(1) = 0;
              ELSE
                EXIT;
              END IF;
              ctmp := regexp_replace(ctmp,strtmp||'([^A-Z0-9\_]{1}|$)','#');
            END LOOP;
          END LOOP;
        END LOOP;
      END IF;

   -- 系统检测:整合原有soutab_chk及其他
   ELSIF i_kind = 'syschk' THEN
      -- 生成所有的检测结果
      DELETE FROM tb_imp_chk WHERE chk_kind IN(SELECT chk_kind FROM tb_imp_chk_inf WHERE engine='ini');
      FOR inf IN (SELECT t.*,ctid AS rid FROM tb_imp_chk_inf t WHERE bpntype=1 AND engine='ini')
      LOOP
          UPDATE tb_imp_chk_inf SET start_time = CURRENT_TIMESTAMP WHERE ctid = inf.rid;
          EXECUTE 'INSERT INTO tb_imp_chk(chk_mobile,chk_sendtype,chk_kind,chk_name,chk_content)' || inf.chk_sql;
          UPDATE tb_imp_chk_inf SET end_time = CURRENT_TIMESTAMP WHERE ctid = inf.rid;
      END LOOP;
      DELETE FROM tb_imp_chk WHERE chk_kind NOT IN(SELECT chk_kind FROM tb_imp_chk_inf);

      -- 配置了短信发送的检测
      FOR c1 IN (SELECT chk_mobile,chk_sendtype,
                        chk_kind || ':' || string_agg(E'\n' || chk_name ||
                          CASE WHEN cnt > 1 THEN '[' || cnt || '个]' END, '') chk_content
                   FROM (SELECT chk_mobile, chk_sendtype, chk_kind, chk_name, count(1) cnt
                           FROM tb_imp_chk
                          WHERE chk_sendtype IS NOT NULL
                          GROUP BY chk_mobile, chk_sendtype, chk_kind, chk_name) as tc1
                  GROUP BY chk_mobile, chk_sendtype, chk_kind)
      LOOP
          PERFORM sp_sms(c1.chk_content,c1.chk_mobile,c1.chk_sendtype);
      END LOOP;

   -- 获取最新的hadoop表结构(不含视图)
   ELSIF i_kind = 'get_hdptbls' THEN
     INSERT INTO tmp_imp(pkid)
     SELECT db_name||'.'||tbl_name FROM (SELECT * FROM tb_imp_etl_tbls_tmp EXCEPT SELECT * FROM tb_imp_etl_tbls) as tp2 GROUP BY db_name||'.'||tbl_name;

     DELETE FROM tb_imp_etl_tbls WHERE db_name||'.'||tbl_name IN(SELECT pkid FROM tmp_imp);
     INSERT INTO tb_imp_etl_tbls
     SELECT * FROM tb_imp_etl_tbls_tmp WHERE db_name||'.'||tbl_name IN(SELECT pkid FROM tmp_imp);

   -- 刷新ODS采集表的源和目标结构,用于字段对比
   ELSIF i_kind = 'colexch_updt' THEN
      -- 本次需要更新的表(如果没有指定更新，则是需要更新全部)
      INSERT INTO tmp_imp(pkid)
      SELECT tid FROM vw_imp_etl WHERE bupdate='n';
      SELECT count(1) INTO v_err FROM tmp_imp;

      -- hdp
      DELETE FROM tb_imp_tbl_hdp WHERE tid IN(SELECT pkid FROM tmp_imp) OR v_err=0 OR tid NOT IN(SELECT tid FROM tb_imp_etl);
      INSERT INTO tb_imp_tbl_hdp(tid,hive_owner,hive_tablename,col_name,col_type_full,col_type,col_precision,col_scale,col_idx,tbl_comment,col_comment,cd_id)
      SELECT tid,hive_owner,hive_tablename,col_name,col_type_full,col_type,col_precision,col_scale,col_idx,tbl_comment,col_comment,cd_id FROM vw_imp_tbl_hdp
      WHERE bupdate='n' OR v_err=0;
      -- sou
      DELETE FROM tb_imp_tbl_sou WHERE tid IN(SELECT pkid FROM tmp_imp) OR v_err=0 OR tid NOT IN(SELECT tid FROM tb_imp_etl);
      INSERT INTO tb_imp_tbl_sou(tid,sou_db_conn,sou_owner,sou_tablename,column_name_orig,column_name,column_id,data_type,data_length,data_precision,data_scale,tbl_comment,col_comment,dest_type,dest_type_full)
      SELECT tid,sou_db_conn,sou_owner,sou_tablename,column_name_orig,column_name,column_id,data_type,data_length,data_precision,data_scale,tbl_comment,col_comment,dest_type,dest_type_full
      FROM vw_imp_tbl_sou WHERE bupdate='n' OR v_err=0;

   END IF;

   -- 记录操作流水
   INSERT INTO tb_imp_jour(kind,trade_date,status,key_id,remark)
   SELECT 'public',v_tradedate,i_kind,v_sp_id,
          v_remark||E'\n'||'开始时间：'||to_char(v_curtime,'YYYYMMDD HH24:MI:SS')||',执行耗时：'||to_char(extract(epoch from (CURRENT_TIMESTAMP-v_curtime))/60,'FM999.99')||'分钟';

EXCEPTION
   WHEN OTHERS THEN
      -- 记录错误信息
      PERFORM sp_sms('sp_imp_alone执行报错,kind=['||i_kind||'],sp_id=['||i_sp_id||'],value1=['||i_value1||'],错误说明=['||SQLERRM||']','1','110');
      RAISE;
END;
$$ LANGUAGE plpgsql;

