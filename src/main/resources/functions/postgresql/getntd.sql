-- PostgreSQL version of getntd
CREATE OR REPLACE FUNCTION getntd()
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT getparam('NTD','C')::integer INTO o_return;
  RETURN o_return;
END;
$$;

