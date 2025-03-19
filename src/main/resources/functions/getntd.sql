CREATE OR REPLACE function STG01.getntd return integer RESULT_CACHE as
  o_return integer;
begin
  select stg01.getparam('NTD', 'C') into o_return from dual;
  return o_return;
end;