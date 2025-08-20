-- PostgreSQL version of getltd
CREATE OR REPLACE FUNCTION getltd()
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT getparam('LTD','C')::integer INTO o_return;
  RETURN o_return;
END;
$$;

