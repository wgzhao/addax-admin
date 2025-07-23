CREATE OR REPLACE FUNCTION fn_imp_freqchk(i_freq varchar)
RETURNS integer AS $$
DECLARE
  o_return integer;
  strfreq varchar(1);
  noffset integer;
BEGIN
  -- 用于判断TD是否符合启动频率(日、周、月、季、年)
  -- 第一位代表启动频率: 期末为大写，期初为小写(例如m代表月初第一个交易日,M代表月底最后一个交易日)
  strfreq := substring(i_freq FROM 1 FOR 1);
  -- 第二三位代表从符合条件的交易日起，之后重复几个交易日
  noffset := COALESCE(substring(i_freq FROM 2 FOR 2)::integer, 1) - 1;
  
  WITH t_param AS (
    -- 算出几个关键的交易日
    SELECT CASE
             WHEN substring(param_kind_0 FROM -1) = '0' THEN
              lower(substring(param_kind_0 FROM 2 FOR 1))
             ELSE
              substring(param_kind_0 FROM 2 FOR 1)
           END AS kind,
           param_value
      FROM vw_imp_param
     WHERE param_sou = 'C'
       AND param_kind_0 IN ('TD', 'CW1', 'CM1', 'CQ1', 'CY1', 'CW0', 'CM0', 'CQ0', 'CY0')
  ),
  t_nextdate AS (
    -- 计算交易日的后续几个交易日
    SELECT init_date,
           LEAD(init_date, noffset) OVER(ORDER BY init_date) AS next_date
      FROM vw_trade_date
  ),
  t_range AS (
    -- 根据入参的执行频率，计算交易日范围
    SELECT param_value AS start_dt, a.next_date AS end_dt
      FROM t_param t
     INNER JOIN t_nextdate a
        ON a.init_date = t.param_value::integer
     WHERE kind = strfreq
  )
  SELECT count(1)
    INTO o_return
    FROM t_range
   WHERE (SELECT param_value::integer FROM t_param WHERE kind = 'D') BETWEEN start_dt::integer AND end_dt::integer;

  RETURN o_return;
END;
$$ LANGUAGE plpgsql;