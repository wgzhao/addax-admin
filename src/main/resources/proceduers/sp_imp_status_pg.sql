CREATE OR REPLACE FUNCTION STG01.sp_imp_status(i_kind varchar, i_sp_id varchar)
RETURNS void AS $$
DECLARE
   v_remark varchar(2000);
   v_kind varchar(32);
   v_err integer;
   v_sou varchar(10);
   v_curtime timestamp;
   c1 RECORD;
BEGIN
   v_kind := i_kind;
   v_curtime := CURRENT_TIMESTAMP;

   -- 获取基础信息
   WITH t_sp AS (
     -- 主表信息
     SELECT sp_id, 'sp' AS sou,
            'SP主表信息：{名称=[' || spname || '],主表状态=[' || flag || '],前置源=[' || need_sou ||
            '],剩余次数=[' || retry_cnt || '],运行耗时=[' || runtime || '],任务组=[' || task_group || '],参数组=[' || param_sou || ']}' AS remark
       FROM vw_imp_sp
      WHERE bvalid = 1
      UNION ALL
     SELECT tid, 'etl',
            'ETL主表信息：{名称=[' || spname || '],源表=[' || sou_db_conn || ':' || sou_owner || '.' || sou_tablename ||
            '],主表状态=[' || flag || '],剩余次数=[' || retry_cnt || '],运行耗时=[' || runtime || '],参数组=[' || param_sou || ']}'
       FROM vw_imp_etl
      WHERE bvalid = 1
      UNION ALL
     SELECT pn_id, 'plan',
            'PLAN主表信息：{名称=[' || spname || '],主表状态=[' || flag || '],运行耗时=[' || runtime || ']}'
       FROM stg01.vw_imp_plan
      UNION ALL
     SELECT ds_id, 'ds',
            'DS主表信息：{名称=[' || ds_name || '],主表状态=[' || flag || '],剩余次数=[' || retry_cnt || '],运行耗时=[' || runtime || '],参数组=[' || param_sou || ']}'
       FROM stg01.vw_imp_ds2
   ),
   t_com AS (
     -- 附属表信息
     SELECT sp_id, com_id, flag, '子表信息：{命令类型=[' || com_kind || '],命令顺序=[' || com_idx || '],命令状态=[' || flag || ']}' AS remark
        FROM stg01.tb_imp_sp_com
       UNION ALL
      SELECT ds_id, tbl_id, flag, '子表信息：{状态=[' || flag || '],目标表=[' || dest_tablename || ']}'
        FROM stg01.tb_imp_ds2_tbls
   )
   SELECT max(t.remark || CASE WHEN length(i_kind) = 2 THEN E'\n' || b.remark ELSE '' END),
          COALESCE(sum(CASE WHEN COALESCE(b.flag, 'N') = 'Y' THEN 0 ELSE 1 END), -1),
          max(sou)
          INTO v_remark, v_err, v_sou
     FROM t_sp t
    INNER JOIN t_com b ON b.sp_id = t.sp_id AND i_sp_id IN (b.sp_id, b.com_id) AND COALESCE(b.flag, 'N') <> 'X'
    WHERE length(i_sp_id) = 32;

   -- 找到对应的主表信息(v_err=-1表示未找到对应记录,0表示正确,大于1表示有错误任务)
   IF v_err >= 0 THEN
     v_kind := CASE WHEN i_kind = 'Y' AND v_err > 0 THEN 'E' ELSE i_kind END;
     
     IF length(v_kind) = 1 THEN
       -- 主表的状态变更（1位字符）
       -- SP计算
       UPDATE stg01.tb_imp_sp
          SET flag       = v_kind,
              start_time = CASE WHEN v_kind = 'R' THEN v_curtime ELSE start_time END,
              end_time   = CASE WHEN v_kind IN ('Y', 'E') THEN v_curtime ELSE end_time END,
              runtime    = CASE WHEN v_kind = 'Y' THEN extract(epoch from (v_curtime - start_time))
                               WHEN v_kind = 'E' THEN runtime / 2 
                               ELSE runtime END,
              retry_cnt  = retry_cnt - CASE WHEN v_kind = 'E' THEN 1 ELSE 0 END
        WHERE sp_id = i_sp_id AND v_sou = 'sp';
        
       -- ODS采集
       UPDATE stg01.tb_imp_etl
          SET flag       = v_kind,
              start_time = CASE WHEN v_kind = 'R' THEN v_curtime ELSE start_time END,
              end_time   = CASE WHEN v_kind IN ('E', 'Y') THEN v_curtime ELSE end_time END,
              runtime    = CASE WHEN v_kind = 'Y' THEN extract(epoch from (v_curtime - start_time))
                               WHEN v_kind = 'E' THEN runtime / 2 
                               ELSE runtime END,
              retry_cnt  = retry_cnt - CASE WHEN v_kind = 'E' THEN 1 ELSE 0 END
        WHERE tid = i_sp_id AND v_sou = 'etl';
        
       -- 计划任务
       UPDATE stg01.tb_imp_plan
          SET flag       = v_kind,
              start_time = CASE WHEN v_kind = 'R' THEN v_curtime ELSE start_time END,
              end_time   = CASE WHEN v_kind IN ('E', 'Y') THEN v_curtime ELSE end_time END,
              runtime    = extract(epoch from (v_curtime - start_time))
        WHERE pn_id = i_sp_id AND v_sou = 'plan';
        
       -- 数据服务
       UPDATE stg01.tb_imp_ds2
          SET flag       = v_kind,
              start_time = CASE WHEN v_kind = 'R' THEN v_curtime ELSE start_time END,
              end_time   = CASE WHEN v_kind IN ('E', 'Y') THEN v_curtime ELSE end_time END,
              runtime    = CASE WHEN v_kind = 'Y' THEN extract(epoch from (v_curtime - start_time))
                               WHEN v_kind = 'E' THEN runtime / 2 
                               ELSE runtime END,
              retry_cnt  = retry_cnt - CASE WHEN v_kind = 'E' THEN 1 ELSE 0 END,
              bupdate    = CASE WHEN v_kind = 'E' THEN 'Y' ELSE bupdate END
        WHERE ds_id = i_sp_id AND v_sou = 'ds';

       IF v_kind = 'R' THEN
         -- 主表开始执行,附属表状态置为N
         UPDATE stg01.tb_imp_sp_com SET flag = 'N'
          WHERE flag <> 'X' AND sp_id = i_sp_id AND v_sou IN ('sp', 'etl', 'plan');

         -- ds_etl:数据服务开始执行，推送列表状态置为N(重跑时仅报错任务置N)
         UPDATE stg01.tb_imp_ds2_tbls
            SET flag = 'N'
          WHERE ds_id = i_sp_id AND v_sou = 'ds'
            AND (
                (COALESCE(flag, 'N') <> 'X' AND (SELECT retry_cnt FROM stg01.tb_imp_ds2 WHERE ds_id = i_sp_id) = 3)
                OR
                (COALESCE(flag, 'E') IN ('E', 'R') AND (SELECT retry_cnt FROM stg01.tb_imp_ds2 WHERE ds_id = i_sp_id) < 3)
                );

       ELSIF v_kind = 'E' THEN
         -- 任务执行结束,报错提醒
         FOR c1 IN (
           SELECT v_sou || ':' || spname || '执行失败!!' || E'\n' || '[' ||
                  to_char(start_time, 'YYYY-MM-DD HH24:MI:SS') || '=>' || to_char(end_time, 'HH24:MI:SS') || ']' ||
                  CASE WHEN v_sou IN ('ds', 'plan') THEN msg2 ELSE '' END AS msg,
                  mobile
           FROM (
             -- sp执行结束
             SELECT sp_id, t.spname, start_time, end_time, COALESCE(a.proj_name || ',1', '1') AS mobile
               FROM stg01.vw_imp_sp t
               LEFT JOIN stg01.vw_ci_deploy a ON a.spname = t.sp_owner || '.' || t.sp_name AND a.bvalid = 1
              WHERE t.sp_id = i_sp_id AND v_sou = 'sp' AND t.retry_cnt = 0 AND t.flag = 'E'
              UNION ALL
             -- 数据服务执行结束
             SELECT ds_id, ds_name, start_time, end_time, '1'
               FROM stg01.vw_imp_ds2
              WHERE ds_id = i_sp_id AND v_sou = 'ds' AND retry_cnt = 0 AND flag = 'E'
              UNION ALL
             -- 计划任务执行结束
             SELECT pn_id, spname, start_time, end_time, '1'
               FROM stg01.vw_imp_plan
              WHERE pn_id = i_sp_id AND v_sou = 'plan' AND flag = 'E'
           ) t
           LEFT JOIN (
             SELECT ds_id,
                    E'\n' || '失败任务' || sum(CASE WHEN flag NOT IN ('Y', 'X') THEN 1 ELSE 0 END) || '个(总任务' || count(1) || '个)' AS msg2
               FROM (
                SELECT ds_id, COALESCE(flag, 'E') AS flag, dest_tablename
                  FROM stg01.tb_imp_ds2_tbls
                 WHERE ds_id = i_sp_id AND v_sou = 'ds'
                 UNION ALL
                SELECT sp_id, COALESCE(flag, 'E'),
                       replace(to_char(substring(com_text FROM '^[^' || E'\n' || ':,]+')), '#')
                  FROM stg01.tb_imp_sp_com
                 WHERE sp_id = i_sp_id AND v_sou = 'plan'
                ) sub
              GROUP BY ds_id
           ) a ON a.ds_id = t.sp_id
         ) LOOP
             PERFORM stg01.sp_sms(c1.msg, c1.mobile, '110');
         END LOOP;

       END IF;

     ELSE
       -- 附属表的状态变更（2位字符）
       UPDATE stg01.tb_imp_sp_com
          SET flag       = substring(v_kind FROM 2 FOR 1),
              start_time = CASE WHEN v_kind = 'cR' THEN v_curtime ELSE start_time END,
              end_time   = CASE WHEN v_kind IN ('cY', 'cE') THEN v_curtime ELSE end_time END
        WHERE com_id = i_sp_id AND flag <> 'X' AND v_sou IN ('sp', 'etl', 'plan');

       UPDATE stg01.tb_imp_ds2_tbls
          SET flag       = substring(v_kind FROM 2 FOR 1),
              start_time = CASE WHEN v_kind = 'cR' THEN v_curtime ELSE start_time END,
              end_time   = CASE WHEN v_kind IN ('cY', 'cE') THEN v_curtime ELSE end_time END
        WHERE tbl_id = i_sp_id AND flag <> 'X' AND v_sou = 'ds';
     END IF;

     -- 记录操作流水
     INSERT INTO stg01.tb_imp_jour(kind, trade_date, status, key_id, remark)
     VALUES (v_sou, gettd(), v_kind, i_sp_id,
            v_remark || E'\n' || '开始时间：' || to_char(v_curtime, 'YYYYMMDD HH24:MI:SS') || ',执行耗时：' || 
            extract(epoch from (CURRENT_TIMESTAMP - v_curtime))::text ||
            '秒==>传入参数：{i_kind=[' || i_kind || '],i_sp_id=[' || i_sp_id || ']}<==');

   END IF;

EXCEPTION
   WHEN OTHERS THEN
        PERFORM stg01.sp_sms('sp_imp_status执行报错,i_kind=[' || i_kind || '],i_sp_id=[' || i_sp_id || '],v_sou=[' || v_sou || '],v_kind=[' || v_kind || '],错误说明=[' || SQLERRM || ']', '18692206867', '110');
        RAISE;
END;
$$ LANGUAGE plpgsql;