-- PostgreSQL version of fn_imp_comment_replace
CREATE OR REPLACE FUNCTION fn_imp_comment_replace(i_text text)
RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
  s text;
BEGIN
  s := i_text;
  -- 移除换行、chr(19)、单引号、双引号、反斜杠
  s := replace(s, E'\n', '');
  s := replace(s, chr(19), '');
  s := replace(s, '''', '');
  s := replace(s, '"', '');
  s := replace(s, E'\\', '');
  RETURN s;
END;
$$;

