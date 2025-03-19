CREATE OR REPLACE function STG01.fn_imp_timechk(i_currtime in date,
                                           i_fixed    in varchar2,
                                           i_interval in int default 0,
                                           i_range    in varchar2 default null,
                                           i_exit     in varchar default 'Y')
  return int RESULT_CACHE as
  o_return int;
  v_range1 int;
  v_range2 int;
begin
  select dt_full into v_range1 from vw_imp_date
  where dt = nvl(regexp_substr(i_range, '^[0-9]+'), 0) ;

  select dt_full into v_range2 from vw_imp_date
  where dt = nvl(regexp_substr(i_range, '[0-9]+$'), 2359) ;

  select count(1)
    into o_return
    from dual
   where (--时间间隔的任务(通过i_currtime-计划开始时间，计算分钟数，然后除以i_interval计算是否符合条件)
         mod(round((i_currtime -
               (trunc(i_currtime)
                -case when to_char(i_currtime,'HH24MI')<v_range1 then 1 else 0 end --计算开始时间，如果跨日，需要将入参日期减1天
                +substr(v_range1,1,2)/24 --计划开始小时
                +substr(v_range1,-2)/24/60) --计划开始分钟
             )*24*60),i_interval) = 0
         and i_interval > 0 and
         (
         (v_range1<v_range2 and to_number(to_char(i_currtime, 'HH24MI')) between v_range1 and v_range2) or
         (v_range1>v_range2 and (to_number(to_char(i_currtime, 'HH24MI'))>=v_range1 or to_number(to_char(i_currtime, 'HH24MI'))<=v_range2))
         )
         )
         or
         --定点的时间任务
         (to_char(i_currtime, 'HH24MI') not between '0001' and '0023' and
         instr(',' || nvl(i_fixed,' ') || ',',
                ',' || nvl(regexp_replace(to_char(i_currtime, 'HH24MI'),
                                          '(^0+|00$)'),
                           '0') || ',') > 0);
  return o_return;
end;