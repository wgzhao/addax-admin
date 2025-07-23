CREATE OR REPLACE FUNCTION sp_imp_param_pg(i_curr_date integer DEFAULT 1)
RETURNS void AS $$
DECLARE
   v_trade_date integer;
   v_curr_date integer;
   v_jump_week integer;
   v_param_sou char(1);
   v_strtmp varchar;
   c1 RECORD;
BEGIN
   v_curr_date := CASE WHEN (i_curr_date/10000000) <> 2 THEN to_char(CURRENT_TIMESTAMP,'YYYYMMDD')::integer ELSE i_curr_date END;

   SELECT MAX(init_date) INTO v_trade_date 
   FROM vw_trade_date 
   WHERE init_date <= v_curr_date;

   v_param_sou := CASE v_curr_date
                     WHEN to_char(CURRENT_TIMESTAMP,'YYYYMMDD')::integer THEN 'C'
                     WHEN to_char(CURRENT_TIMESTAMP - interval '1 day','YYYYMMDD')::integer THEN 'L'
                     WHEN to_char(CURRENT_TIMESTAMP + interval '1 day','YYYYMMDD')::integer THEN 'N'
                     ELSE 'T' END;

   -- 日期处理逻辑转换
   -- 动态SQL重构
   -- UNPIVOT改用PostgreSQL的jsonb解包方式
   -- 异常处理块调整
   -- 日期计算逻辑转换
   SELECT CASE WHEN count(1)=0 THEN 7 ELSE 0 END INTO v_jump_week
   FROM vw_trade_date
   WHERE init_date BETWEEN to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD') - interval '1 week') - interval '7 days', 'YYYYMMDD')::integer
                     AND to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '1 day', 'YYYYMMDD')::integer;

   -- 动态SQL重构（UNPIVOT改用jsonb）
   IF v_param_sou IN('C','L','N') THEN
     EXECUTE format($dynsql$
       INSERT INTO tb_imp_param0
       SELECT key as param_kind, value::text as param_value, %L as param_sou
       FROM jsonb_each_text(
         jsonb_build_object(
           'TRADE_FLAG', CASE WHEN %L = %L THEN 'Y' ELSE 'N' END,
           'CURR_DATE', %L,
           'TRADE_MONTH', to_char(to_date(%L::text, 'YYYYMMDD'), 'YYYYMM')
         )
       )
     $dynsql$, v_param_sou, v_trade_date, v_curr_date, v_curr_date, v_trade_date);

     -- 创建参数视图（PostgreSQL版本）
     IF v_param_sou = 'C' THEN
       EXECUTE (
         SELECT 'CREATE OR REPLACE VIEW vw_imp_param_all AS '||
                string_agg(DISTINCT format('max(CASE param_kind WHEN %L THEN param_value END) as %I', param_kind_0, param_kind_0), ',')
         FROM tb_imp_param0
         WHERE param_sou='C'
       );
     END IF;

   ELSE
     PERFORM sp_sms('参数更新条件不满足','1','111');
   END IF;

IF v_param_sou IN('C','L','N') THEN
     EXECUTE format($dynsql$
       INSERT INTO tb_imp_param0
       SELECT key as param_kind, value::text as param_value, %L as param_sou
       FROM jsonb_each_text(
         jsonb_build_object(
           'TRADE_FLAG', CASE WHEN %L = %L THEN 'Y' ELSE 'N' END,
           'CURR_DATE', %L,
           'TRADE_MONTH', to_char(to_date(%L::text, 'YYYYMMDD'), 'YYYYMM'),
           'LASTDATE_30', to_char(to_date(%L::text, 'YYYYMMDD') -  30, 'YYYYMMDD'),
           'LASTDATE_90', to_char(to_date(%L::text, 'YYYYMMDD') -  90, 'YYYYMMDD'),
           'LASTDATE_180', to_char(to_date(%L::text, 'YYYYMMDD') -  180, 'YYYYMMDD')
           'LASTDATE_365', to_char(to_date(%L::text, 'YYYYMMDD') -  365, 'YYYYMMDD')
           'LASTDATE_730', to_char(to_date(%L::text, 'YYYYMMDD') -  730, 'YYYYMMDD')
         )
       )
     $dynsql$, v_param_sou, v_trade_date, v_curr_date, v_curr_date, v_trade_date, v_trade_date, v_trade_date, v_trade_date);

     -- 补充月份、季度、年度参数生成逻辑
     EXECUTE $
       INSERT INTO tb_imp_param0
       SELECT * FROM (
         SELECT 
           param_kind, 
           CASE
           WHEN param_kind LIKE '%DATE_%' THEN 
             to_char(to_date(v_trade_date::text, 'YYYYMMDD') - 
             CASE 
               WHEN param_kind = 'LASTDATE_365' THEN 365
               WHEN param_kind = 'LASTDATE_730' THEN 730
             END * interval '1 day', 'YYYYMMDD')
           WHEN param_kind LIKE '%MONTH' THEN
             to_char(date_trunc('month', to_date(v_trade_date::text, 'YYYYMMDD') - 
             (CASE 
               WHEN param_kind = 'LAST_TRADE_MONTH' THEN 1
               WHEN param_kind = 'LAST2_TRADE_MONTH' THEN 2
             END) * interval '1 month'), 'YYYYMM')
           WHEN param_kind LIKE '%QUART' THEN
             to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD') - 
             (CASE 
               WHEN param_kind = 'LAST_TRADE_QUART' THEN 3
             END) * interval '1 month'), 'YYYYMM')
           WHEN param_kind LIKE '%YEAR' THEN
             to_char(date_trunc('year', to_date(v_trade_date::text, 'YYYYMMDD') - 
             (CASE 
               WHEN param_kind = 'LAST_TRADE_YEAR' THEN 12
               WHEN param_kind = 'LAST2_TRADE_YEAR' THEN 24
             END) * interval '1 month'), 'YYYY')
           WHEN param_kind LIKE 'CURWEEK_%' THEN
             to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) + 
             (CASE 
               WHEN param_kind = 'CURWEEK_MONDAY' THEN 0
               WHEN param_kind = 'CURWEEK_SUNDAY' THEN 6
             END) * interval '1 day', 'YYYYMMDD')
           END::text
         FROM (VALUES
           ('LASTDATE_365'),('LASTDATE_730'),
           ('TRADE_MONTH'),('LAST_TRADE_MONTH'),
           ('LAST2_TRADE_MONTH'),('TRADE_QUART'),
           ('LAST_TRADE_QUART'),('TRADE_YEAR'),
           ('LAST_TRADE_YEAR'),('LAST2_TRADE_YEAR'),
           ('CURWEEK_MONDAY'),('CURWEEK_SUNDAY')
         ) t(param_kind)
       ) sub;
     $;

     -- 创建参数视图（PostgreSQL版本）
     IF v_param_sou = 'C' THEN
       EXECUTE (
         SELECT 'CREATE OR REPLACE VIEW vw_imp_param_all AS SELECT '||
                string_agg(DISTINCT format('MAX(CASE param_kind WHEN %L THEN param_value END) AS %I', param_kind, param_kind), ',')||
                ' FROM tb_imp_param0'
         FROM tb_imp_param0
         WHERE param_sou='C'
       );
     END IF;

   ELSE
     PERFORM sp_sms('参数更新条件不满足','1','111');
   END IF;

EXCEPTION
   WHEN others THEN
      RAISE EXCEPTION 'sp_imp_param_pg执行错误: %%', SQLERRM;
END;
$$ LANGUAGE plpgsql;