-- PostgreSQL version of fn_imp_freqchk
CREATE OR REPLACE FUNCTION fn_imp_freqchk(i_freq text)
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  o_return integer := 0;
  strfreq  text;
  noffset  integer;
BEGIN
  -- i_freq 规则：首位为周期(D/W/M/Q/Y)，大写=期末，小写=期初；后两位为从该关键交易日起偏移的交易日数
  IF i_freq IS NULL OR length(i_freq) = 0 THEN
    RETURN 0;
  END IF;
  strfreq := substr(i_freq, 1, 1);
  noffset := COALESCE((substr(i_freq, 2, 2))::integer, 1) - 1;

  WITH t_param AS (
    -- 计算几个关键交易日，kind 映射：末位为0 => 使用小写（期初），否则大写（期末）；TD -> kind='D'
    SELECT CASE WHEN substr(param_kind_0, length(param_kind_0), 1) = '0'
                THEN lower(substr(param_kind_0, 2, 1))
                ELSE substr(param_kind_0, 2, 1)
           END AS kind,
           param_value::integer AS param_value
      FROM vw_imp_param
     WHERE param_sou = 'C'
       AND param_kind_0 IN ('TD','CW1','CM1','CQ1','CY1','CW0','CM0','CQ0','CY0')
  ),
  t_nextdate AS (
    SELECT init_date::integer AS init_date,
           lead(init_date::integer, noffset) OVER(ORDER BY init_date::integer) AS next_date
      FROM vw_trade_date
  ),
  t_range AS (
    SELECT p.param_value AS start_dt, n.next_date AS end_dt
      FROM t_param p
      JOIN t_nextdate n ON n.init_date = p.param_value
     WHERE p.kind = strfreq
  )
  SELECT COUNT(1)
    INTO o_return
    FROM t_range r
   WHERE (SELECT param_value::integer FROM vw_imp_param WHERE param_sou='C' AND param_kind_0='TD' LIMIT 1)
         BETWEEN r.start_dt AND r.end_dt;

  RETURN o_return;
END;
$$;
