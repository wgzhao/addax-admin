-- PostgreSQL version of fn_imp_pnname
-- 注意参数类型与返回类型
CREATE OR REPLACE FUNCTION fn_imp_pnname(
  i_pntype   text,
  i_fixed    text DEFAULT NULL,
  i_interval integer DEFAULT NULL,
  i_range    text DEFAULT NULL
) RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
  o_return  text;
BEGIN
  -- 计划类型名称
  SELECT entry_content
    INTO o_return
    FROM tb_dictionary
   WHERE entry_code = '1064'
     AND entry_value = i_pntype
   LIMIT 1;

  -- 计划类型具体定义
  IF i_pntype IS NOT NULL AND NOT (i_fixed IS NULL AND i_interval IS NULL AND i_range IS NULL) THEN
    o_return := o_return || CASE
                 WHEN i_fixed IS NOT NULL THEN '定时' || i_fixed
                 ELSE COALESCE(i_range,'') || '内_间隔' || COALESCE(i_interval::text,'') || '分钟'
               END;
  END IF;

  RETURN o_return;
END;
$$;
