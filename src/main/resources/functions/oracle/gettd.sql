CREATE OR REPLACE function gettd return integer RESULT_CACHE as
  o_return integer;
begin
  select getparam('TD', 'C') into o_return from dual;
  return o_return;
end;