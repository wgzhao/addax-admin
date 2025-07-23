CREATE OR REPLACE FUNCTION fn_imp_pnname(i_pntype varchar,
                                         i_fixed varchar DEFAULT NULL,
                                         i_interval integer DEFAULT NULL,
                                         i_range varchar DEFAULT NULL)
RETURNS varchar AS $$
DECLARE
  o_return varchar(255);
  v_strname varchar(255);
BEGIN
  -- 计划类型名称
  SELECT entry_content
    INTO o_return
    FROM tb_dictionary
   WHERE entry_code = '1064'
     AND entry_value = i_pntype;
     
  -- 计划类型具体定义
  IF i_pntype IS NOT NULL AND
     NOT (i_fixed IS NULL AND i_interval IS NULL AND i_range IS NULL) THEN
    o_return := o_return || CASE
                  WHEN i_fixed IS NOT NULL THEN
                   '定时' || i_fixed
                  ELSE
                   i_range || '内_间隔' || i_interval || '分钟'
                END;
  END IF;

  RETURN o_return;
END;
$$ LANGUAGE plpgsql;