CREATE OR REPLACE FUNCTION STG01.fn_imp_comment_replace(i_text text)
RETURNS text AS $$
BEGIN
  RETURN replace(replace(replace(replace(replace(i_text, E'\n', ''), chr(19), ''), '''', ''), '"', ''), '\', '');
END;
$$ LANGUAGE plpgsql;