CREATE OR REPLACE function STG01.fn_imp_freqchk(i_freq in varchar2) return int RESULT_CACHE as
  o_return int;
  strfreq  varchar2(1);
  noffset  int;
begin
  --用于判断TD是否符合启动频率(日、周、月、季、年)
  --第一位代表启动频率:  期末为大写，期初为小写(例如m代表月初第一个交易日,M代表月底最后一个交易日)
  strfreq := substr(i_freq, 1, 1);
  --第二三位代表从符合条件的交易日起，之后重复几个交易日
  noffset := nvl(substr(i_freq, 2, 2), 1) - 1;
  with t_param as --算出几个关键的交易日
   (select case
             when substr(param_kind_0, -1) = '0' then
              lower(substr(param_kind_0, 2, 1))
             else
              substr(param_kind_0, 2, 1)
           end kind,
           param_value
      from vw_imp_param
     where param_sou = 'C'
       and param_kind_0 in
           ('TD', 'CW1', 'CM1', 'CQ1', 'CY1', 'CW0', 'CM0', 'CQ0', 'CY0')),
  t_nextdate as --计算交易日的后续几个交易日
   (select init_date,
           lead(init_date, noffset) over(order by init_date) next_date
      from vw_trade_date),
  t_range as --根据入参的执行频率，计算交易日范围
   (select param_value start_dt, a.next_date end_dt
      from t_param t
     inner join t_nextdate a
        on a.init_date = t.param_value
     where kind = strfreq)
  select count(1)
    into o_return
    from t_range
   where (select param_value from t_param where kind = 'D') between
         start_dt and end_dt;

  return o_return;
end;