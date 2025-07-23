CREATE OR REPLACE FUNCTION getntd() 
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT getparam('NTD', 'C') INTO o_return;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;