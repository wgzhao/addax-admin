-- PostgreSQL version of getparam
CREATE OR REPLACE FUNCTION getparam(
  i_date_kind text DEFAULT 'TD',
  i_param_sou text DEFAULT 'C'
) RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
  o_return text;
BEGIN
  SELECT param_value
    INTO o_return
    FROM vw_imp_param
   WHERE param_kind_0 = i_date_kind
     AND param_sou = i_param_sou
   LIMIT 1;
  RETURN o_return;
END;
$$;

