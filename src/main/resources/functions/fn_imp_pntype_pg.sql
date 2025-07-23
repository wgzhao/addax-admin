CREATE OR REPLACE FUNCTION STG01.fn_imp_pntype(i_pn_type varchar)
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  /*
  i_pn_type入参说明
  0  每天
  1  交易标志为Y
  2  交易日当天
  3  交易日或标志
  */
  SELECT CASE
           WHEN i_pn_type = '0' THEN 1
           WHEN i_pn_type IN ('2', '3') AND to_char(CURRENT_TIMESTAMP, 'YYYYMMDD') IN (gettd()::text, getntd()::text) THEN 1
           WHEN i_pn_type IN ('1', '3') AND getparam('TF') = 'Y' THEN 1
           ELSE 0
         END
    INTO o_return;
    
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;