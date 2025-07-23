CREATE OR REPLACE FUNCTION STG01.getltd() 
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT STG01.getparam('LTD', 'C') INTO o_return;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;