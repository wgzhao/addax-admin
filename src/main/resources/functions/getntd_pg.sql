CREATE OR REPLACE FUNCTION STG01.getntd() 
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT STG01.getparam('NTD', 'C') INTO o_return;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;