CREATE OR REPLACE FUNCTION STG01.gettd() 
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT STG01.getparam('TD', 'C') INTO o_return;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;