-- PostgreSQL version of fn_imp_pntype
CREATE OR REPLACE FUNCTION fn_imp_pntype(i_pn_type text)
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  o_return integer := 0;
BEGIN
  -- 0:每天; 1:交易标志为Y; 2:交易日当天; 3:交易日或标志
  IF i_pn_type = '0'
     OR (i_pn_type IN ('2','3') AND to_char(current_date,'YYYYMMDD') IN (gettd()::text, getntd()::text))
     OR (i_pn_type IN ('1','3') AND getparam('TF','C') = 'Y') THEN
    o_return := 1;
  END IF;
  RETURN o_return;
END;
$$;

