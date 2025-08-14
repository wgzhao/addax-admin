CREATE OR REPLACE function getntd return integer RESULT_CACHE as
  o_return integer;
begin
  select getparam('NTD', 'C') into o_return from dual;
  return o_return;
end;