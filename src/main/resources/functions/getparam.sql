CREATE OR REPLACE function STG01.getparam(i_date_kind in varchar2 := 'TD',
                                         i_param_sou in varchar2 := 'C')
  return varchar2 RESULT_CACHE as
  o_return varchar2(32);
begin
  select param_value
    into o_return
    from stg01.vw_imp_param
   where param_kind_0 = i_date_kind
     and param_sou = i_param_sou;
  return o_return;
end;