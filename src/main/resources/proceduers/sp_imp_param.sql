CREATE OR REPLACE procedure STG01.sp_imp_param(i_curr_date in integer default 1)
as
       v_trade_date integer;
       v_curr_date integer;
       v_jump_week integer; --������������������������������������������������������
       v_param_sou char(1);
       v_strtmp varchar2(4000);
begin
       v_curr_date := case when trunc(nvl(i_curr_date,1)/10000000)<>2 then to_number(to_char(sysdate,'YYYYMMDD')) else i_curr_date end ;
       select max(init_date) into v_trade_date from stg01.vw_trade_date where init_date<= v_curr_date ;
       v_param_sou := case v_curr_date
                           when to_char(sysdate,'YYYYMMDD') then 'C'
                           when to_char(sysdate-1,'YYYYMMDD') then 'L'
                           when to_char(sysdate+1,'YYYYMMDD') then 'N'
                      else 'T' end ;
       select case when count(1)=0 then 7 else 0 end into v_jump_week
       from stg01.vw_trade_date
       where init_date between to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d')-7,'YYYYMMDD') and to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d')-1,'YYYYMMDD') ;

       --��������������������������������������������������������
       if (v_param_sou in('C','L','N') and to_char(sysdate,'HH24MI') between '1625' and '1635') or v_param_sou='T' then

         delete from stg01.tb_imp_param0 where param_sou = v_param_sou ;
         insert into stg01.tb_imp_param0(param_name,param_value,param_sou,param_kind,param_kind_0,param_remark)
         select '$$'||param_kind,param_value,v_param_sou,case when a.entry_value is not null then '${'||a.entry_value||'}' end,a.entry_value,a.remark
         from (
           --���������������������
           select param_kind, to_char(param_value) param_value
             from (select t.*
               from (select init_date as trade_date,
                            lag(init_date, 1) over(order by init_date) last_trade_date,
                            lead(init_date, 1) over(order by init_date) next_trade_date,
                            lag(init_date, 4) over(order by init_date) last5_trade_date,
                            lag(init_date, 9) over(order by init_date) last10_trade_date,
                            lag(init_date, 19) over(order by init_date) last20_trade_date,
                            lag(init_date, 29) over(order by init_date) last30_trade_date,
                            lag(init_date, 39) over(order by init_date) last40_trade_date,
                            lag(init_date, 59) over(order by init_date) last60_trade_date,
                            lag(init_date, 89) over(order by init_date) last90_trade_date,
                            lag(init_date, 179) over(order by init_date) last180_trade_date,
                            trunc(lag(init_date, 4) over(order by init_date)/100) last5_trade_date_m
                       from stg01.vw_trade_date) t
              where trade_date = v_trade_date)
            unpivot(param_value for param_kind in(trade_date,last_trade_date,next_trade_date,last5_trade_date,last10_trade_date,last20_trade_date,last30_trade_date,last40_trade_date,last60_trade_date,last90_trade_date,last180_trade_date,last5_trade_date_m))

           --���������������������
          union all
          select 'TRADE_FLAG',case when v_trade_date = v_curr_date then 'Y' else 'N' end from dual union all
          select 'CURR_DATE',to_char(v_curr_date) from dual union all
          --select 'LASTDATE',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-1,'YYYYMMDD') from dual union all
          --select 'LASTDATE_FR',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-60,'YYYYMMDD') from dual union all
          select 'LASTDATE_30',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-30,'YYYYMMDD') from dual union all
          select 'LASTDATE_90',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-90,'YYYYMMDD') from dual union all
          select 'LASTDATE_180',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-180,'YYYYMMDD') from dual union all
          select 'LASTDATE_365',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-365,'YYYYMMDD') from dual union all
          select 'LASTDATE_730',to_char(to_date(to_char(v_trade_date),'YYYYMMDD')-730,'YYYYMMDD') from dual union all
          select 'TRADE_MONTH',to_char(to_date(to_char(v_trade_date),'YYYYMMDD'),'YYYYMM') from dual union all
          select 'LAST_TRADE_MONTH',to_char(add_months(to_date(to_char(v_trade_date),'YYYYMMDD'),-1),'YYYYMM') from dual union all
          select 'LAST2_TRADE_MONTH',to_char(add_months(to_date(to_char(v_trade_date),'YYYYMMDD'),-2),'YYYYMM') from dual union all
          select 'TRADE_QUART',to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q'),'YYYYMM') from dual union all
          select 'LAST_TRADE_QUART',to_char(trunc(add_months(to_date(v_trade_date,'YYYYMMDD'),-3),'Q'),'YYYYMM') from dual union all
          select 'TRADE_YEAR',to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'Y'),'YYYY') from dual union all
          select 'LAST_TRADE_YEAR',to_char(add_months(trunc(to_date(v_trade_date,'YYYYMMDD'),'Y'),-12),'YYYY') from dual union all
          select 'LAST2_TRADE_YEAR',to_char(add_months(trunc(to_date(v_trade_date,'YYYYMMDD'),'Y'),-24),'YYYY') from dual union all
          select 'CURWEEK_MONDAY',to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d')+1,'YYYYMMDD') from dual union all
          select 'CURWEEK_SUNDAY',to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d')+7,'YYYYMMDD') from dual

          --month,year
          union all
          select prefix || kind || replace(param_kind, 'C', '_'), to_char(param_value)
          from (select min(init_date) as c0, max(init_date) as c1,
                       decode(row_number() over(order by substr(init_date, 1, 6) desc),1,'CUR',2,'LAST',3,'LAST2') prefix,
                       'MONTH' kind
                  from stg01.vw_trade_date
                 where substr(init_date, 1, 6) between to_char(add_months(to_date(v_trade_date, 'YYYYMMDD'), -2), 'YYYYMM') and substr(v_trade_date, 1, 6)
                 group by substr(init_date, 1, 6)
                 union all
                select min(init_date), max(init_date),
                       decode(row_number() over(order by substr(init_date, 1, 4) desc),1,'CUR',2,'LAST',3,'LAST2') prefix,
                       'YEAR' kind
                  from stg01.vw_trade_date
                 where substr(init_date, 1, 4) between substr(v_trade_date, 1, 4) - 2 and substr(v_trade_date, 1, 4)
                 group by substr(init_date, 1, 4)
           ) unpivot(param_value for param_kind in(C0, C1))

          union all
          select kind||replace(param_kind,'C','_'), to_char(param_value)
          from ( --lastweek
                 select min(init_date) as c0,max(init_date) as c1,'LASTWEEK' kind
                 from stg01.vw_trade_date
                 where init_date between to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d')-7-v_jump_week,'YYYYMMDD') and to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d')-1-v_jump_week,'YYYYMMDD')
                 union all
                 --curweek
                 select min(init_date),max(init_date),'CURWEEK'
                 from stg01.vw_trade_date
                 where init_date between to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'d'),'YYYYMMDD') and to_char(trunc(to_date(v_trade_date,'YYYYMMDD')+7,'d')-1,'YYYYMMDD')
                 union all
                 --curquart
                 select min(init_date),max(init_date),'CURQUART'
                 from stg01.vw_trade_date
                 where substr(init_date,1,6) between to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q'),'YYYYMM') and to_char(add_months(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q'),3)-1,'YYYYMM')
                 union all
                 --lastquart
                 select min(init_date),max(init_date),'LASTQUART'
                 from stg01.vw_trade_date
                 where substr(init_date,1,6) between to_char(add_months(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q'),-3),'YYYYMM') and to_char(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q')-1,'YYYYMM')
                 union all
                 --last2quart
                 select min(init_date),max(init_date),'LAST2QUART'
                 from stg01.vw_trade_date
                 where substr(init_date,1,6) between to_char(add_months(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q'),-6),'YYYYMM') and to_char(add_months(trunc(to_date(v_trade_date,'YYYYMMDD'),'Q'),-4),'YYYYMM')
          ) unpivot (param_value for param_kind in(C0,C1))

          --other
          union all
          select param_kind,to_char(param_value)
          from (
            select 'LASTYEAR_TRADE_DATE' param_kind,max(init_date) param_value
            from stg01.vw_trade_date
            where init_date<=to_number(to_char(add_months(to_date(to_char(v_trade_date),'YYYYMMDD'),-12),'YYYYMMDD'))
            --���������������������������������
            union all
            select 'LASTDATE_30_TRADE',min(init_date) from vw_trade_date where init_date >= to_char(to_date(v_trade_date,'YYYYMMDD') - 30, 'YYYYMMDD') union all
            select 'LASTDATE_90_TRADE',min(init_date) from vw_trade_date where init_date >= to_char(to_date(v_trade_date,'YYYYMMDD') - 90, 'YYYYMMDD') union all
            select 'LASTDATE_180_TRADE',min(init_date) from vw_trade_date where init_date >= to_char(to_date(v_trade_date,'YYYYMMDD') - 180, 'YYYYMMDD') union all
            select 'LASTDATE_365_TRADE',min(init_date) from vw_trade_date where init_date >= to_char(to_date(v_trade_date,'YYYYMMDD') - 365, 'YYYYMMDD') union all
            select 'LASTDATE_730_TRADE',min(init_date) from vw_trade_date where init_date >= to_char(to_date(v_trade_date,'YYYYMMDD') - 730, 'YYYYMMDD') union all
            select 'LASTDATE_800_TRADE',min(init_date) from vw_trade_date where init_date >= to_char(to_date(v_trade_date,'YYYYMMDD') - 800, 'YYYYMMDD')
          )
       ) t
       left join stg01.tb_dictionary a on a.entry_code = '2004' and a.entry_content = '$$'||t.param_kind ;

       --������������������������������������(���������������������)
       if v_param_sou = 'C' then
         v_strtmp := 'create or replace view stg01.vw_imp_param_all as select * from (select param_kind_0,param_value from tb_imp_param0 where param_sou=''C'') pivot (max(param_value) for param_kind_0 in(';
         for c1 in(select param_kind_0 as pk from tb_imp_param0 where param_sou='C' order by param_kind_0)
         loop
             v_strtmp:=v_strtmp||''''||c1.pk||''' as '||c1.pk||',';
         end loop;
         v_strtmp:=regexp_replace(v_strtmp,',$','))');
         execute immediate v_strtmp;
       end if ;
       commit ;

     else
       stg01.sp_sms('���������������������������������������������������!!!','1','111');
     end if ;
end ;