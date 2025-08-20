-- PostgreSQL version of sp_imp_deal
CREATE OR REPLACE FUNCTION sp_imp_deal(i_kind varchar, i_key varchar DEFAULT '')
RETURNS void AS $$
DECLARE
  strtmp1 varchar(4000);
  strtmp2 varchar(4000);
  ctmp1 text;
  ctmp2 text;
  ntmp integer;
  v_curtime timestamp;
  sqlengine varchar(2000);
  c1 record;
  c2 record;
BEGIN
  v_curtime := CURRENT_TIMESTAMP;

  -- 目前支持的SQL引擎
  SELECT 'presto|shell|hive|clickhouse|allsql|crmdb|' || string_agg(dbs, '|')
    INTO sqlengine
    FROM (
      SELECT CASE db_kind_full
               WHEN 'mysql' THEN 'my'
               ELSE 'ora'
             END || '_(' || string_agg(sysid, ',') || ')' AS dbs
        FROM vw_imp_system_allsql
       WHERE db_kind_full IN ('mysql','oracle')
       GROUP BY db_kind_full
    ) t;

  IF i_kind = 'git_deploy' AND length(COALESCE(i_key, ' ')) = 32 THEN
    INSERT INTO tmp_ci_deploy_add
      WITH t_cur AS ( -- 本次情况
         SELECT lower(file_name) AS file_name, file_md5, length(file_content) AS file_length
           FROM tb_ci_deploy
          WHERE dep_id = i_key
       ),
       t_last AS ( -- 上次情况
         SELECT lower(file_name) AS file_name, file_md5, length(file_content) AS file_length
           FROM tb_ci_deploy
          WHERE dep_id = (SELECT last_dep_id FROM vw_ci_deploy_id WHERE dep_id = i_key)
       ),
       t_change AS ( -- 脚本比对情况: 0, '更新', 1, '新增', 3, '置死'
         SELECT COALESCE(t.file_name, a.file_name) AS file_name,
                CASE
                  WHEN t.file_md5 <> a.file_md5 OR t.file_length <> a.file_length THEN 0 -- 更新
                  WHEN a.file_name IS NULL THEN 1 -- 新增
                  WHEN t.file_name IS NULL THEN 3 -- 置死
                  ELSE 9
                END AS add_kind
           FROM t_cur t
           FULL JOIN t_last a ON a.file_name = t.file_name
       )
      -- 对脚本做预处理
      SELECT COALESCE(a.sp_id, gen_random_uuid()::text) AS sp_id,
             COALESCE(a.sp_owner, substring(x.file_name FROM '^[^\.]+')) AS sp_owner,
             COALESCE(a.sp_name, substring(regexp_replace(x.file_name, '\.sql$', ''), '[^\.]+$')) AS sp_name,
             CASE
               WHEN a.sp_id IS NULL AND x.add_kind = 0 THEN 1
               WHEN a.sp_id IS NOT NULL AND x.add_kind = 1 THEN 0
               ELSE x.add_kind
             END AS add_kind,
             replace(
               CASE WHEN NOT (file_content ~ ('^\s*\-{4}(' || sqlengine || ')\-{4}' || E'\n'))
                    THEN '----presto----' || E'\n' || file_content -- 处理代码前面没有写引擎的，用默认引擎
                    ELSE file_content
               END,
               E'\t', '    ' -- TAB改为四个空格，解决前台页面不显示TAB的问题
             ) AS com_text
        FROM t_change x
        LEFT JOIN tb_ci_deploy t ON t.dep_id = i_key AND x.file_name = lower(t.file_name)
        LEFT JOIN tb_imp_sp a ON a.sp_owner || '.' || a.sp_name = regexp_replace(x.file_name, '\.sql$', '')
       WHERE x.add_kind < 9
         AND x.file_name ~ '^[a-z0-9_]+\.[a-z0-9_]+\.sql$';

    -- 新增脚本插入配置主表
    INSERT INTO tb_imp_sp(sp_id, sp_owner, sp_name, task_group)
    SELECT sp_id, sp_owner, sp_name,
           CASE WHEN sp_owner IN ('sjzl','qmfx') THEN sp_owner END
      FROM tmp_ci_deploy_add
     WHERE add_kind = 1;

    -- 置死脚本
    UPDATE tb_imp_sp SET flag = 'X'
     WHERE sp_id IN (SELECT sp_id FROM tmp_ci_deploy_add WHERE add_kind = 3);

    -- 将脚本插入命令列表
    DELETE FROM tb_imp_sp_com WHERE sp_id IN (SELECT sp_id FROM tmp_ci_deploy_add);

    FOR c1 IN (
      SELECT sp_id,
             com_text || E'\n' || '----shell----' || E'\n' AS com_text,
             (length(COALESCE(com_text, ' ')) - length(replace(COALESCE(com_text, ' '),
               '----', ''))) / 4 AS com_num
        FROM tmp_ci_deploy_add
    ) LOOP
        -- 避免一个项目中不同目录出现重复名称
        DELETE FROM tb_imp_sp_com WHERE sp_id = c1.sp_id;
        ctmp1 := c1.com_text;

        -- 如果脚本内容注明了分段,则分段插入命令明细表
        FOR c2 IN 1..c1.com_num LOOP
              -- 获取当前分段标志
              strtmp1 := substring(ctmp1 FROM '\-{4}(' || sqlengine || ')\-{4}' || E'\n');
              -- 删除当前分段标志整行
              ctmp1 := regexp_replace(ctmp1, '^\s*\-{4}(' || sqlengine || ')\-{4}' || E'\n', '');
              -- 计算下一个分段标志的位置,用于截取代码
              ntmp := position(substring(ctmp1 FROM '\-{4}(' || sqlengine || ')\-{4}' || E'\n') IN ctmp1);
              -- 将当前分段代码插入命令明细表(代码少于5个字符的，认定为无效，无需插入命令表)
              ctmp2 := substring(ctmp1 FROM 1 FOR ntmp - 1);

              INSERT INTO tb_imp_sp_com(sp_id, com_idx, com_kind, com_text)
              SELECT c1.sp_id, c2 * 10, substring(strtmp1 FROM '[a-z_]+'), ctmp2
               WHERE length(ctmp2) > 5;

              -- 删除当前分段代码
              ctmp1 := substring(ctmp1 FROM ntmp);
        END LOOP;
    END LOOP;

    -- hadoop中所有在用的schema及sp_owner
    strtmp2 := fn_imp_value('get_schema');

    -- 将需要处理的表，对代码部分进行预处理
    INSERT INTO tmp_imp_sp_needtab (sp_id, sp_name, com_text)
    SELECT t.sp_id,
           upper(t.sp_owner || '.' || t.sp_name) AS sp_name,
           regexp_replace(
             replace(
               regexp_replace(
                 regexp_replace(
                   regexp_replace(
                     regexp_replace(
                       replace(
                         regexp_replace(
                           regexp_replace(
                             replace(
                               replace(
                                 replace(upper(com_text), '--NEED', ''),
                                 '/*', E'\x01'
                               ),
                               '*/', E'\x02'
                             ), -- 替换特殊字符
                             E'\x01[^\x02]*\x02', ' ' -- 删除/* */的注释
                           ),
                           '--.*'
                         ),
                         E'\n', ' ' -- 删除--的注释
                       ),
                       '\s*\.\s*', '.'
                     ),
                     '[^0-9A-Z_]{1}' || upper(t.sp_owner || '\.' || t.sp_name) || '[^0-9A-Z_]{1}', ' # '
                   ),
                   '''[^'']*''', ''
                 ),
                 '(INSERT\s+(INTO|OVERWRITE\s+TABLE)\s+|CREATE(\s+|\s+OR\s+REPLACE\s+)VIEW\s+)', 'INSERT INTO '
               ),
               'HIVE.', ''
             ),
             ' (CLICKHOUSE|CRMDB|MY_RTDB|CRM_DATA)\.', ' XXX_'
           ) AS com_text
      FROM tmp_ci_deploy_add t;

    -- 删除本次新增SP的涉及表及目标表
    DELETE FROM tb_imp_sp_needtab
     WHERE kind IN ('ALL','DEST')
       AND sp_id IN (SELECT sp_id FROM tmp_imp_sp_needtab);

    -- 得到脚本中涉及自身之外的其他所有表
    FOR c1 IN (SELECT * FROM tmp_imp_sp_needtab) LOOP
              ctmp1 := c1.com_text;
              FOR idx IN 1..100 LOOP
                  strtmp1 := substring(ctmp1 FROM '(' || strtmp2 || ')\.[A-Z0-9_]+');
                  IF strtmp1 IS NOT NULL THEN
                      -- 将找到的表插入前置表,不包含SP名字自身
                      INSERT INTO tb_imp_sp_needtab(sp_id, table_name, kind)
                      SELECT c1.sp_id, strtmp1, 'ALL'
                       WHERE NOT EXISTS (
                         SELECT 1 FROM tb_imp_sp_needtab
                          WHERE kind = 'ALL' AND sp_id = c1.sp_id AND table_name = strtmp1
                       ) AND strtmp1 <> c1.sp_name;
                      -- 将找到的表，从源代码中删除，避免再次匹配上,也避免前置表中出现重复的匹配表
                      ctmp1 := regexp_replace(
                        regexp_replace(' ' || ctmp1 || ' ',
                          '[^A-Z0-9_]{1}' || replace(strtmp1, '.', '\.') || '[^A-Z0-9_]{1}', '#'),
                        '^[^#]*#', ''
                      );
                  ELSE
                      EXIT;
                  END IF;
              END LOOP;
    END LOOP;

    -- 得到脚本中除自身和edw_check_log之外的所有目标表
    FOR c1 IN (SELECT * FROM tmp_imp_sp_needtab WHERE com_text LIKE '%INSERT INTO%') LOOP
              ctmp1 := c1.com_text;
              FOR idx IN 1..100 LOOP
                  strtmp1 := regexp_replace(
                    substring(ctmp1 FROM 'INSERT INTO [0-9A-Z_]+\.[0-9A-Z_]+'),
                    'INSERT INTO ', ''
                  );
                  IF strtmp1 IS NOT NULL THEN
                      INSERT INTO tb_imp_sp_needtab(sp_id, table_name, kind)
                      SELECT c1.sp_id, strtmp1, 'DEST'
                       WHERE NOT EXISTS (
                         SELECT 1 FROM tb_imp_sp_needtab
                          WHERE kind = 'DEST' AND sp_id = c1.sp_id AND table_name = strtmp1
                       ) AND strtmp1 <> c1.sp_name;
                      ctmp1 := regexp_replace(
                        regexp_replace(ctmp1, 'INSERT INTO ' || strtmp1 || '[^A-Z0-9_]{1}', '#'),
                        '^[^#]*#', ''
                      );
                  ELSE
                      EXIT;
                  END IF;
              END LOOP;
    END LOOP;

    -- 计算出SP涉及的所有前置(基于SP计算最近一层的依赖)
    DELETE FROM tb_imp_sp_needtab WHERE kind = 'NEEDS';
    INSERT INTO tb_imp_sp_needtab (sp_id, table_name, kind)
      SELECT sp_id, needs_sp_id, 'NEEDS'
        FROM (
          SELECT t.sp_id, a.sp_id AS needs_sp_id
            FROM tb_imp_sp_needtab t
           INNER JOIN vw_imp_sp a ON upper(a.sp_owner || '.' || a.sp_name) = t.table_name
                  AND a.sp_id <> t.sp_id
           WHERE t.kind = 'ALL'
          UNION
          SELECT t.sp_id, a.sp_id
            FROM tb_imp_sp_needtab t
           INNER JOIN tb_imp_sp_needtab a ON a.table_name = t.table_name
                  AND a.sp_id <> t.sp_id
                  AND a.kind = 'DEST'
           WHERE t.kind = 'ALL'
          UNION
          SELECT t.sp_id, COALESCE(a.need_sou, 'UF') AS need_sou
            FROM vw_imp_sp t
            LEFT JOIN (
              SELECT sp_id, substring(table_name FROM 4 FOR 2) AS need_sou
                FROM tb_imp_sp_needtab
               WHERE kind = 'ALL'
                 AND table_name LIKE 'ODS%'
            ) a ON a.sp_id = t.sp_id
        ) s;

    -- SP的计算层级，层级之内并行，层级之间串行
    DELETE FROM tb_imp_sp_needtab WHERE kind ~ 'RUNLEV\d+';

    -- 只依赖数据源的SP，即为初始层
    INSERT INTO tb_imp_sp_needtab(sp_id, kind)
    SELECT sp_id, 'RUNLEV0'
      FROM vw_imp_sp
     WHERE bvalid = 1
       AND sp_id NOT IN (
         SELECT sp_id FROM vw_imp_sp_needs WHERE length(needs) = 32
       );

    -- 其他层级
    FOR c1 IN 1..20 LOOP
      INSERT INTO tb_imp_sp_needtab(sp_id, kind)
      SELECT t.sp_id, 'RUNLEV' || c1
        FROM vw_imp_sp_needs t
        LEFT JOIN tb_imp_sp_needtab a ON a.sp_id = t.needs AND a.kind ~ 'RUNLEV\d+'
       WHERE length(t.needs) = 32
         AND t.sp_id NOT IN (SELECT sp_id FROM tb_imp_sp_needtab WHERE kind ~ 'RUNLEV\d+')
       GROUP BY t.sp_id
      HAVING count(1) = sum(CASE WHEN a.sp_id IS NOT NULL THEN 1 ELSE 0 END);
    END LOOP;

    -- SP穿透后的依赖
    DELETE FROM tb_imp_sp_needtab WHERE kind = 'NDS';
    INSERT INTO tb_imp_sp_needtab(kind, sp_id, table_name)
    SELECT 'NDS', sp_id, needs
      FROM vw_imp_sp_needs
     WHERE length(needs) = 32
       AND sp_id IN (SELECT sp_id FROM tb_imp_sp_needtab WHERE kind LIKE 'RUNLEV%');

    FOR c1 IN 1..10 LOOP
      INSERT INTO tb_imp_sp_needtab(kind, sp_id, table_name)
      SELECT DISTINCT kind, t.sp_id, a.needs
        FROM tb_imp_sp_needtab t
       INNER JOIN vw_imp_sp_needs a ON a.sp_id = t.table_name AND length(a.needs) = 32
       WHERE kind = 'NDS'
         AND NOT EXISTS (
           SELECT 1 FROM tb_imp_sp_needtab
            WHERE kind = 'NDS' AND sp_id = t.sp_id AND table_name = a.needs
         );
    END LOOP;

    -- 更新SP依赖显示表，仅影响前台页面展示，具体的调起依赖，直接使用的源表
    DELETE FROM tb_imp_sp_needall;
    INSERT INTO tb_imp_sp_needall
    WITH t_sp AS (
      SELECT DISTINCT sp_id, table_name, kind
        FROM tb_imp_sp_needtab t
       WHERE t.kind IN ('ALL', 'NEEDS', 'DEST', 'NDS')
    )
    SELECT t.sp_id,
           -- SP直接的依赖（贴源层）
           regexp_replace(
             string_agg(
               CASE WHEN kind = 'NEEDS' AND length(table_name) = 2
                    THEN table_name || ','
               END,
               '' ORDER BY table_name
             ),
             ',$', ''
           ) AS need_sou,
           string_agg(
             CASE WHEN kind = 'NEEDS' AND length(table_name) = 32
                  THEN a.spname || E'\n'
             END,
             '' ORDER BY a.spname
           ) AS need_sp,
           string_agg(
             CASE WHEN kind = 'ALL'
                  THEN table_name || E'\n'
             END,
             '' ORDER BY a.spname
           ) AS sp_alltabs,
           string_agg(
             CASE WHEN kind = 'DEST'
                  THEN table_name || E'\n'
             END,
             '' ORDER BY a.spname
           ) AS sp_dest,
           -- SP穿透后的全部依赖
           regexp_replace(
             string_agg(
               CASE WHEN kind = 'NDS' AND length(table_name) = 2
                    THEN table_name || ','
               END,
               '' ORDER BY table_name
             ),
             ',$', ''
           ) AS through_need_sou,
           replace(
             string_agg(
               CASE WHEN kind = 'NDS' AND length(table_name) = 32
                    THEN a.spname
               END,
               ',' ORDER BY a.spname
             ),
             ',', E'\n'
           ) AS through_need_sp
      FROM t_sp t
      LEFT JOIN (
        SELECT sp_id, sp_owner || '.' || sp_name AS spname
          FROM tb_imp_sp
      ) a ON a.sp_id = t.table_name
     GROUP BY t.sp_id;

    -- 提醒信息
    SELECT 'git发起代码提交!!' || E'\n' ||
           COALESCE(
             substring(
               regexp_replace(
                 string_agg(msg || E'\n', '' ORDER BY msg),
                 E'\n$', ''
               )
               FROM 1 FOR 3000
             ),
             '没有脚本变更'
           )
      INTO strtmp1
      FROM (
        SELECT sp_owner ||
               CASE add_kind
                 WHEN 0 THEN '更新'
                 WHEN 1 THEN '新增'
                 WHEN 2 THEN '复活'
                 WHEN 3 THEN '置死'
               END ||
               count(1) || '个:[' || string_agg(sp_name, ',') || ']' AS msg
          FROM tmp_ci_deploy_add
         GROUP BY sp_owner, add_kind
      ) t;

    -- 计算短信接收人
    SELECT '1,' || string_agg(proj_name, ',')
      INTO strtmp2
      FROM (
        SELECT proj_name
          FROM tb_ci_deploy
         WHERE dep_id = i_key
         GROUP BY proj_name
      ) t;

    PERFORM sp_sms(strtmp1, strtmp2, '010');

  END IF;

  -- 记录操作流水
  INSERT INTO tb_imp_jour(kind, trade_date, status, key_id, remark)
  SELECT 'public', gettd(), i_kind, i_key,
         '开始时间：' || to_char(v_curtime, 'YYYYMMDD HH24:MI:SS') ||
         ',执行耗时：' || to_char(extract(epoch from (CURRENT_TIMESTAMP - v_curtime)), 'FM999999999') ||
         '秒==>传入参数：{i_kind=[' || i_kind || '],i_key=[' || i_key || ']}<===';

EXCEPTION
   WHEN OTHERS THEN
      PERFORM sp_sms('sp_imp_deal执行报错,kind=[' || i_kind || '],key=[' || i_key ||
                     '],错误说明=[' || substring(SQLERRM FROM 1 FOR 200) || ']', '18692206867', '110');
      RAISE;
END;
$$ LANGUAGE plpgsql;

