CREATE OR REPLACE FUNCTION gettd() 
RETURNS integer AS $$
DECLARE
  o_return integer;
BEGIN
  SELECT getparam('TD', 'C') INTO o_return;
  RETURN o_return;
END;
$$ LANGUAGE plpgsql;