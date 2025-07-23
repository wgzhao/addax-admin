CREATE OR REPLACE FUNCTION fn_imp_timechk(i_currtime timestamp,
                                           i_fixed varchar,
                                           i_interval integer DEFAULT 0,
                                           i_range varchar DEFAULT NULL,
                                           i_exit varchar DEFAULT 'Y')
RETURNS integer AS $$
DECLARE
  o_return integer;
  v_range1 integer;
  v_range2 integer;
BEGIN
  SELECT dt_full INTO v_range1 FROM vw_imp_date
  WHERE dt = COALESCE(substring(i_range FROM '^[0-9]+')::integer, 0);

  SELECT dt_full INTO v_range2 FROM vw_imp_date
  WHERE dt = COALESCE(substring(i_range FROM '[0-9]+$')::integer, 2359);

  SELECT CASE
           WHEN (
             -- 时间间隔的任务(通过i_currtime-计划开始时间，计算分钟数，然后除以i_interval计算是否符合条件)
             MOD(ROUND((i_currtime -
                   (date_trunc('day', i_currtime)
                    - CASE WHEN to_char(i_currtime,'HH24MI')::integer < v_range1 THEN interval '1 day' ELSE interval '0 day' END -- 计算开始时间，如果跨日，需要将入参日期减1天
                    + (v_range1::text::integer / 100) * interval '1 hour' -- 计划开始小时
                    + (v_range1::text::integer % 100) * interval '1 minute' -- 计划开始分钟
                   )) * 24 * 60), i_interval) = 0
             AND i_interval > 0 AND
             (
             (v_range1 < v_range2 AND to_char(i_currtime, 'HH24MI')::integer BETWEEN v_range1 AND v_range2) OR
             (v_range1 > v_range2 AND (to_char(i_currtime, 'HH24MI')::integer >= v_range1 OR to_char(i_currtime, 'HH24MI')::integer <= v_range2))
             )
           ) OR (
             -- 定点的时间任务
             to_char(i_currtime, 'HH24MI') NOT BETWEEN '0001' AND '0023' AND
             position(',' || COALESCE(regexp_replace(to_char(i_currtime, 'HH24MI'), '(^0+|00$)', '0', 'g'), '0') || ',' 
                     IN ',' || COALESCE(i_fixed, ' ') || ',') > 0
           ) THEN 1
           ELSE 0
         END
    INTO o_return;
    
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;