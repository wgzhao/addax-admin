-- PostgreSQL version of fn_imp_param_replace
CREATE OR REPLACE FUNCTION fn_imp_param_replace(
  i_com_text  text,
  i_param_sou text DEFAULT 'C'
) RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
  o_return text;
  r RECORD;
BEGIN
  o_return := i_com_text;

  -- 根据参数文件替换变量
  FOR r IN (
    SELECT param_kind, param_value
      FROM vw_imp_param
     WHERE param_sou = i_param_sou
       AND param_kind IS NOT NULL
  ) LOOP
    o_return := replace(o_return, r.param_kind, r.param_value);
  END LOOP;

  -- 替换通用的特殊参数值
  o_return := replace(o_return, '${NOW}', to_char(current_timestamp, 'YYYY-MM-DD HH24:MI:SS'));
  o_return := replace(o_return, '${NO}', to_char(current_timestamp, 'YYYYMMDD'));
  -- 需要 pgcrypto 扩展以使用 gen_random_uuid()
  o_return := replace(o_return, '${UUID}', gen_random_uuid()::text);

  RETURN o_return;
END;
$$;
