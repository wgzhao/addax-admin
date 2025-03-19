CREATE OR REPLACE function STG01.gettd return integer RESULT_CACHE as
  o_return integer;
begin
  select stg01.getparam('TD', 'C') into o_return from dual;
  return o_return;
end;