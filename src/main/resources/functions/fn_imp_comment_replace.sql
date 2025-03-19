CREATE OR REPLACE function STG01.fn_imp_comment_replace(i_text in clob)
  return clob as
begin

  return replace(replace(replace(replace(replace(i_text, chr(10)), chr(19)), ''''), '"'), '\') ;

end;