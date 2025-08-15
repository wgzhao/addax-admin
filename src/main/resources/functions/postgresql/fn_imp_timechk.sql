-- PostgreSQL version of fn_imp_timechk
-- Params:
--   i_currtime: 当前时间戳
--   i_fixed   : 逗号分隔的定点时间（如 '8,930,14,1800'，支持去前导0、去末尾00）
--   i_interval: 轮询间隔（分钟），>0 生效
--   i_range   : 时间范围，形如 'HHMM-HHMM'，支持跨日，如 '2300-0130'；为空时按 0000-2359
--   i_exit    : 预留参数（与 Oracle 保持一致），此实现未使用
-- Return: 0/1（不满足/满足）

CREATE OR REPLACE FUNCTION fn_imp_timechk(
  i_currtime timestamp without time zone,
  i_fixed    text,
  i_interval integer DEFAULT 0,
  i_range    text DEFAULT NULL,
  i_exit     text DEFAULT 'Y'
) RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  o_return   integer := 0;
  v_range1_s text;
  v_range2_s text;
  v_range1   integer;
  v_range2   integer;
  plan_start timestamp;
  elapsed_min integer;
  curr_hhmi   integer;
  curr_token  text;
  cond_interval boolean := false;
  cond_fixed    boolean := false;
BEGIN
  -- 取范围起止（dt_full），默认 0000-2359
  SELECT COALESCE((SELECT dt_full::text FROM vw_imp_date WHERE dt = COALESCE((substring(i_range FROM '^[0-9]+'))::numeric, 0)), '0000')
    INTO v_range1_s;
  SELECT COALESCE((SELECT dt_full::text FROM vw_imp_date WHERE dt = COALESCE((substring(i_range FROM '[0-9]+$'))::numeric, 2359)), '2359')
    INTO v_range2_s;

  -- 规范为 4 位
  v_range1_s := lpad(v_range1_s, 4, '0');
  v_range2_s := lpad(v_range2_s, 4, '0');
  v_range1 := v_range1_s::integer;
  v_range2 := v_range2_s::integer;

  -- 计算计划开始时间（考虑跨日）
  plan_start := date_trunc('day', i_currtime)
                - CASE WHEN to_char(i_currtime,'HH24MI')::integer < v_range1 THEN interval '1 day' ELSE interval '0 day' END
                + make_interval(hours := substr(v_range1_s,1,2)::integer,
                                mins  := substr(v_range1_s,3,2)::integer);

  -- 间隔匹配：分钟差对 i_interval 取模
  IF i_interval > 0 THEN
    elapsed_min := round(extract(epoch FROM (i_currtime - plan_start)) / 60.0)::integer;
    curr_hhmi   := to_char(i_currtime,'HH24MI')::integer;

    cond_interval := (elapsed_min % i_interval = 0) AND (
        (v_range1 < v_range2 AND curr_hhmi BETWEEN v_range1 AND v_range2)
        OR
        (v_range1 > v_range2 AND (curr_hhmi >= v_range1 OR curr_hhmi <= v_range2))
    );
  END IF;

  -- 定点匹配：过滤 00:01~00:23；并支持去前导 0 与去末尾 00
  IF NOT (to_char(i_currtime,'HH24MI') BETWEEN '0001' AND '0023') THEN
    curr_token := regexp_replace(to_char(i_currtime,'HH24MI'), '(^0+|00$)', '', 'g');
    IF curr_token IS NULL OR curr_token = '' THEN
      curr_token := '0';
    END IF;
    cond_fixed := position(','||curr_token||',' IN (','||COALESCE(i_fixed,' ')||',')) > 0;
  END IF;

  o_return := CASE WHEN cond_interval OR cond_fixed THEN 1 ELSE 0 END;
  RETURN o_return;
END;
$$;
