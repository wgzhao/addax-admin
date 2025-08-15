-- PostgreSQL version of sp_imp_param
CREATE OR REPLACE FUNCTION sp_imp_param(i_curr_date integer DEFAULT 1)
RETURNS void AS $$
DECLARE
   v_trade_date integer;
   v_curr_date integer;
   v_jump_week integer;
   v_param_sou char(1);
   v_strtmp varchar(4000);
   c1 record;
BEGIN
   v_curr_date := CASE WHEN trunc(COALESCE(i_curr_date, 1) / 10000000) <> 2
                       THEN to_char(CURRENT_TIMESTAMP, 'YYYYMMDD')::integer
                       ELSE i_curr_date
                  END;

   SELECT max(init_date) INTO v_trade_date
     FROM vw_trade_date
    WHERE init_date <= v_curr_date;

   v_param_sou := CASE v_curr_date
                    WHEN to_char(CURRENT_TIMESTAMP, 'YYYYMMDD')::integer THEN 'C'
                    WHEN to_char(CURRENT_TIMESTAMP - interval '1 day', 'YYYYMMDD')::integer THEN 'L'
                    WHEN to_char(CURRENT_TIMESTAMP + interval '1 day', 'YYYYMMDD')::integer THEN 'N'
                    ELSE 'T'
                  END;

   SELECT CASE WHEN count(1) = 0 THEN 7 ELSE 0 END INTO v_jump_week
     FROM vw_trade_date
    WHERE init_date BETWEEN
          to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '7 days', 'YYYYMMDD')::integer
      AND to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '1 day', 'YYYYMMDD')::integer;

   -- 参数更新条件判断
   IF (v_param_sou IN ('C','L','N') AND to_char(CURRENT_TIMESTAMP, 'HH24MI') BETWEEN '1625' AND '1635')
      OR v_param_sou = 'T' THEN

     DELETE FROM tb_imp_param0 WHERE param_sou = v_param_sou;

     INSERT INTO tb_imp_param0(param_name, param_value, param_sou, param_kind, param_kind_0, param_remark)
     SELECT '$$' || param_kind, param_value, v_param_sou,
            CASE WHEN a.entry_value IS NOT NULL THEN '${' || a.entry_value || '}' END,
            a.entry_value, a.remark
       FROM (
         -- 交易日相关参数
         WITH trade_dates AS (
           SELECT init_date AS trade_date,
                  lag(init_date, 1) OVER (ORDER BY init_date) AS last_trade_date,
                  lead(init_date, 1) OVER (ORDER BY init_date) AS next_trade_date,
                  lag(init_date, 4) OVER (ORDER BY init_date) AS last5_trade_date,
                  lag(init_date, 9) OVER (ORDER BY init_date) AS last10_trade_date,
                  lag(init_date, 19) OVER (ORDER BY init_date) AS last20_trade_date,
                  lag(init_date, 29) OVER (ORDER BY init_date) AS last30_trade_date,
                  lag(init_date, 39) OVER (ORDER BY init_date) AS last40_trade_date,
                  lag(init_date, 59) OVER (ORDER BY init_date) AS last60_trade_date,
                  lag(init_date, 89) OVER (ORDER BY init_date) AS last90_trade_date,
                  lag(init_date, 179) OVER (ORDER BY init_date) AS last180_trade_date,
                  trunc(lag(init_date, 4) OVER (ORDER BY init_date) / 100) AS last5_trade_date_m
             FROM vw_trade_date
         )
         SELECT * FROM (
           SELECT 'trade_date' AS param_kind, trade_date::text AS param_value FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last_trade_date', last_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'next_trade_date', next_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last5_trade_date', last5_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last10_trade_date', last10_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last20_trade_date', last20_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last30_trade_date', last30_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last40_trade_date', last40_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last60_trade_date', last60_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last90_trade_date', last90_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last180_trade_date', last180_trade_date::text FROM trade_dates WHERE trade_date = v_trade_date
           UNION ALL
           SELECT 'last5_trade_date_m', last5_trade_date_m::text FROM trade_dates WHERE trade_date = v_trade_date
         ) t WHERE param_value IS NOT NULL

         -- 其他日期参数
         UNION ALL
         SELECT 'TRADE_FLAG', CASE WHEN v_trade_date = v_curr_date THEN 'Y' ELSE 'N' END
         UNION ALL
         SELECT 'CURR_DATE', v_curr_date::text
         UNION ALL
         SELECT 'LASTDATE_30', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '30 days', 'YYYYMMDD')
         UNION ALL
         SELECT 'LASTDATE_90', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '90 days', 'YYYYMMDD')
         UNION ALL
         SELECT 'LASTDATE_180', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '180 days', 'YYYYMMDD')
         UNION ALL
         SELECT 'LASTDATE_365', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '365 days', 'YYYYMMDD')
         UNION ALL
         SELECT 'LASTDATE_730', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '730 days', 'YYYYMMDD')
         UNION ALL
         SELECT 'TRADE_MONTH', to_char(to_date(v_trade_date::text, 'YYYYMMDD'), 'YYYYMM')
         UNION ALL
         SELECT 'LAST_TRADE_MONTH', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '1 month', 'YYYYMM')
         UNION ALL
         SELECT 'LAST2_TRADE_MONTH', to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '2 months', 'YYYYMM')
         UNION ALL
         SELECT 'TRADE_QUART', to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')), 'YYYYMM')
         UNION ALL
         SELECT 'LAST_TRADE_QUART', to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD') - interval '3 months'), 'YYYYMM')
         UNION ALL
         SELECT 'TRADE_YEAR', to_char(date_trunc('year', to_date(v_trade_date::text, 'YYYYMMDD')), 'YYYY')
         UNION ALL
         SELECT 'LAST_TRADE_YEAR', to_char(date_trunc('year', to_date(v_trade_date::text, 'YYYYMMDD') - interval '12 months'), 'YYYY')
         UNION ALL
         SELECT 'LAST2_TRADE_YEAR', to_char(date_trunc('year', to_date(v_trade_date::text, 'YYYYMMDD') - interval '24 months'), 'YYYY')
         UNION ALL
         SELECT 'CURWEEK_MONDAY', to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) + interval '1 day', 'YYYYMMDD')
         UNION ALL
         SELECT 'CURWEEK_SUNDAY', to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) + interval '7 days', 'YYYYMMDD')

         -- 月份和年度参数
         UNION ALL
         SELECT prefix || kind || replace(param_kind, 'C', '_'), param_value::text
         FROM (
           SELECT min(init_date) AS c0, max(init_date) AS c1,
                  CASE row_number() OVER (ORDER BY substring(init_date::text FROM 1 FOR 6) DESC)
                    WHEN 1 THEN 'CUR'
                    WHEN 2 THEN 'LAST'
                    WHEN 3 THEN 'LAST2'
                  END AS prefix,
                  'MONTH' AS kind
             FROM vw_trade_date
            WHERE substring(init_date::text FROM 1 FOR 6) BETWEEN
                  to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '2 months', 'YYYYMM')
                  AND substring(v_trade_date::text FROM 1 FOR 6)
            GROUP BY substring(init_date::text FROM 1 FOR 6)
           UNION ALL
           SELECT min(init_date), max(init_date),
                  CASE row_number() OVER (ORDER BY substring(init_date::text FROM 1 FOR 4) DESC)
                    WHEN 1 THEN 'CUR'
                    WHEN 2 THEN 'LAST'
                    WHEN 3 THEN 'LAST2'
                  END AS prefix,
                  'YEAR' AS kind
             FROM vw_trade_date
            WHERE substring(init_date::text FROM 1 FOR 4)::integer BETWEEN
                  substring(v_trade_date::text FROM 1 FOR 4)::integer - 2
                  AND substring(v_trade_date::text FROM 1 FOR 4)::integer
            GROUP BY substring(init_date::text FROM 1 FOR 4)
         ) t1
         CROSS JOIN (VALUES ('C0', c0), ('C1', c1)) AS t2(param_kind, param_value)

         -- 周参数
         UNION ALL
         SELECT kind || replace(param_kind, 'C', '_'), param_value::text
         FROM (
           -- lastweek
           SELECT min(init_date) AS c0, max(init_date) AS c1, 'LASTWEEK' AS kind
             FROM vw_trade_date
            WHERE init_date BETWEEN
                  to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '7 days' - (v_jump_week || ' days')::interval, 'YYYYMMDD')::integer
                  AND to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '1 day' - (v_jump_week || ' days')::interval, 'YYYYMMDD')::integer
           UNION ALL
           -- curweek
           SELECT min(init_date), max(init_date), 'CURWEEK'
             FROM vw_trade_date
            WHERE init_date BETWEEN
                  to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')), 'YYYYMMDD')::integer
                  AND to_char(date_trunc('week', to_date(v_trade_date::text, 'YYYYMMDD')) + interval '6 days', 'YYYYMMDD')::integer
           UNION ALL
           -- curquart
           SELECT min(init_date), max(init_date), 'CURQUART'
             FROM vw_trade_date
            WHERE substring(init_date::text FROM 1 FOR 6) BETWEEN
                  to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')), 'YYYYMM')
                  AND to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')) + interval '3 months' - interval '1 day', 'YYYYMM')
           UNION ALL
           -- lastquart
           SELECT min(init_date), max(init_date), 'LASTQUART'
             FROM vw_trade_date
            WHERE substring(init_date::text FROM 1 FOR 6) BETWEEN
                  to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '3 months', 'YYYYMM')
                  AND to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '1 day', 'YYYYMM')
           UNION ALL
           -- last2quart
           SELECT min(init_date), max(init_date), 'LAST2QUART'
             FROM vw_trade_date
            WHERE substring(init_date::text FROM 1 FOR 6) BETWEEN
                  to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '6 months', 'YYYYMM')
                  AND to_char(date_trunc('quarter', to_date(v_trade_date::text, 'YYYYMMDD')) - interval '4 months', 'YYYYMM')
         ) t3
         CROSS JOIN (VALUES ('C0', c0), ('C1', c1)) AS t4(param_kind, param_value)

         -- 其他参数
         UNION ALL
         SELECT 'LASTYEAR_TRADE_DATE', max(init_date)::text
           FROM vw_trade_date
          WHERE init_date <= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '12 months', 'YYYYMMDD')::integer
         UNION ALL
         SELECT 'LASTDATE_30_TRADE', min(init_date)::text FROM vw_trade_date WHERE init_date >= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '30 days', 'YYYYMMDD')::integer
         UNION ALL
         SELECT 'LASTDATE_90_TRADE', min(init_date)::text FROM vw_trade_date WHERE init_date >= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '90 days', 'YYYYMMDD')::integer
         UNION ALL
         SELECT 'LASTDATE_180_TRADE', min(init_date)::text FROM vw_trade_date WHERE init_date >= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '180 days', 'YYYYMMDD')::integer
         UNION ALL
         SELECT 'LASTDATE_365_TRADE', min(init_date)::text FROM vw_trade_date WHERE init_date >= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '365 days', 'YYYYMMDD')::integer
         UNION ALL
         SELECT 'LASTDATE_730_TRADE', min(init_date)::text FROM vw_trade_date WHERE init_date >= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '730 days', 'YYYYMMDD')::integer
         UNION ALL
         SELECT 'LASTDATE_800_TRADE', min(init_date)::text FROM vw_trade_date WHERE init_date >= to_char(to_date(v_trade_date::text, 'YYYYMMDD') - interval '800 days', 'YYYYMMDD')::integer
       ) t
       LEFT JOIN tb_dictionary a ON a.entry_code = '2004' AND a.entry_content = '$$' || t.param_kind;

     -- 创建参数视图（仅在当前参数时创建）
     IF v_param_sou = 'C' THEN
       SELECT 'CREATE OR REPLACE VIEW vw_imp_param_all AS SELECT ' ||
              string_agg('MAX(CASE WHEN param_kind_0 = ''' || param_kind_0 || ''' THEN param_value END) AS ' || param_kind_0, ',') ||
              ' FROM tb_imp_param0 WHERE param_sou = ''C'''
         INTO v_strtmp
         FROM tb_imp_param0
        WHERE param_sou = 'C'
          AND param_kind_0 IS NOT NULL;

       EXECUTE v_strtmp;
     END IF;

   ELSE
     PERFORM sp_sms('参数更新条件不满足!!!', '1', '111');
   END IF;

EXCEPTION
   WHEN OTHERS THEN
      PERFORM sp_sms('sp_imp_param执行报错,错误说明=[' || SQLERRM || ']', '1', '110');
      RAISE;
END;
$$ LANGUAGE plpgsql;
