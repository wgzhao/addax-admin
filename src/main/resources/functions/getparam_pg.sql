CREATE OR REPLACE FUNCTION getparam(i_date_kind varchar DEFAULT 'TD',
                                         i_param_sou varchar DEFAULT 'C')
RETURNS varchar AS $$
DECLARE
  o_return varchar(32);
BEGIN
  SELECT param_value
    INTO o_return
    FROM vw_imp_param
   WHERE param_kind_0 = i_date_kind
     AND param_sou = i_param_sou;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;