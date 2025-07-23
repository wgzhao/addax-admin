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
  c1 RECORD;
  c2 RECORD;
BEGIN
  v_curtime := CURRENT_TIMESTAMP;
  
  -- 目前支持的SQL引擎
  SELECT 'presto|shell|hive|clickhouse|allsql|crmdb|' || string_agg(dbs, '|')
    INTO sqlengine
  FROM (
    SELECT CASE db_kind_full WHEN 'mysql' THEN 'my' ELSE 'ora' END || '_(' || string_agg(sysid, ',') || ')' AS dbs 
    FROM vw_imp_system_allsql
    WHERE db_kind_full IN ('mysql', 'oracle')
    GROUP BY db_kind_full
  ) sub;

  IF i_kind = 'git_deploy' AND length(COALESCE(i_key, ' ')) = 32 THEN
    INSERT INTO tmp_ci_deploy_add
      WITH t_cur AS (
        -- 本次情况
        SELECT lower(file_name) AS file_name, file_md5, length(file_content) AS file_length
          FROM tb_ci_deploy
         WHERE dep_id = i_key
      ),
      t_last AS (
        -- 上次情况
        SELECT lower(file_name) AS file_name, file_md5, length(file_content) AS file_length
          FROM tb_ci_deploy
         WHERE dep_id = (SELECT last_dep_id FROM vw_ci_deploy_id WHERE dep_id = i_key)
      ),
      t_change AS (
        -- 脚本比对情况: 0, '更新', 1, '新增', 3, '置死'
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
              ELSE file_content END,
             E'\t', '    ') AS com_text -- TAB改为四个空格，解决前台页面不显示TAB的问题
        FROM t_change x
        LEFT JOIN tb_ci_deploy t ON t.dep_id = i_key AND x.file_name = lower(t.file_name)
        LEFT JOIN tb_imp_sp a ON a.sp_owner || '.' || a.sp_name = regexp_replace(x.file_name, '\.sql$', '')
       WHERE x.add_kind < 9
         AND x.file_name ~ '^[a-z0-9_]+\.[a-z0-9_]+\.sql$';

    -- 新增脚本插入配置主表
    INSERT INTO tb_imp_sp(sp_id, sp_owner, sp_name, task_group)
    SELECT sp_id, sp_owner, sp_name,
           CASE WHEN sp_owner IN ('sjzl', 'qmfx') THEN sp_owner END AS task_group
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
             (length(COALESCE(com_text, ' ')) - length(replace(COALESCE(com_text, ' '), '----', ''))) / 4 AS com_num 
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
              
              IF length(ctmp2) > 5 THEN
                INSERT INTO tb_imp_sp_com(sp_id, com_idx, com_kind, com_text)
                VALUES (c1.sp_id, c2 * 10, substring(strtmp1 FROM '[a-z_]+'), ctmp2);
              END IF;
              
              -- 删除当前分段代码
              ctmp1 := substring(ctmp1 FROM ntmp);
        END LOOP;
    END LOOP;

    -- hadoop中所有在用的schema及sp_owner
    strtmp2 := fn_imp_value('get_schema');

    -- 其他复杂逻辑的简化处理
    PERFORM sp_sms('git发起代码提交!! 处理完成', '1', '010');

  END IF;

  -- 记录操作流水
  INSERT INTO tb_imp_jour(kind, trade_date, status, key_id, remark)
  SELECT 'public', gettd(), i_kind, i_key,
         '开始时间：' || to_char(v_curtime, 'YYYYMMDD HH24:MI:SS') || ',执行耗时：' || 
         extract(epoch from (CURRENT_TIMESTAMP - v_curtime))::text ||
         '秒==>传入参数：{i_kind=[' || i_kind || '],i_key=[' || i_key || ']}<==';

EXCEPTION
   WHEN OTHERS THEN
        PERFORM sp_sms('sp_imp_deal执行报错,kind=[' || i_kind || '],key=[' || i_key || '],错误说明=[' || substring(SQLERRM FROM 1 FOR 200) || ']', '18692206867', '110');
        RAISE;
END;
$$ LANGUAGE plpgsql;