CREATE OR REPLACE function fn_imp_pntype(i_pn_type in varchar2)
  return int RESULT_CACHE as
  o_return int;
begin
  /*
  i_pn_type入参说明
  0  每天
  1  交易标志为Y
  2  交易日当天
  3  交易日或标志
  */
  select count(1)
    into o_return
    from dual
   where i_pn_type = '0'
      or (i_pn_type in ('2', '3') and to_char(sysdate, 'YYYYMMDD') in (gettd(), getntd()))
      or (i_pn_type in ('1', '3') and getparam('TF')='Y');
    return o_return;
end;