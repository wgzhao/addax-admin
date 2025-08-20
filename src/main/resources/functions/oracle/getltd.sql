CREATE OR REPLACE function getltd return integer RESULT_CACHE as
  o_return integer;
begin
  select getparam('LTD', 'C') into o_return from dual;
  return o_return;
end;