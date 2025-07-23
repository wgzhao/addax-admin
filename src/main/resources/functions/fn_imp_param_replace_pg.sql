CREATE OR REPLACE FUNCTION fn_imp_param_replace(i_com_text text, i_param_sou varchar DEFAULT 'C')
RETURNS text AS $$
DECLARE
  o_return text;
  c1 RECORD;
BEGIN
  o_return := i_com_text;
  
  -- 根据参数文件替换变量
  FOR c1 IN (SELECT a.param_kind, a.param_value
             FROM vw_imp_param a
             WHERE a.param_sou = i_param_sou
               AND a.param_kind IS NOT NULL) LOOP
    o_return := replace(o_return, c1.param_kind, c1.param_value);
  END LOOP;
  
  -- 替换通用的特殊参数值
  o_return := replace(replace(replace(o_return, 
                      '${NOW}', to_char(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS')),
                      '${NO}', to_char(CURRENT_TIMESTAMP, 'YYYYMMDD')),
                      '${UUID}', replace(gen_random_uuid()::text, '-', ''));
  
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;