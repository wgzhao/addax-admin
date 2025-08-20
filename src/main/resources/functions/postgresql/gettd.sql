-- PostgreSQL version of gettd
CREATE OR REPLACE FUNCTION gettd()
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT getparam('TD','C')::integer INTO o_return;
  RETURN o_return;
END;
$$;

