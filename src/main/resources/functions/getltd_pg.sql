CREATE OR REPLACE FUNCTION getltd() 
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT getparam('LTD', 'C') INTO o_return;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;