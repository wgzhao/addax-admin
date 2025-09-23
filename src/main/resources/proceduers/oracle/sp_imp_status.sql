CREATE OR REPLACE procedure sp_imp_status(i_kind in varchar2,i_sp_id in varchar2)
as
   v_remark varchar2(2000);
   v_kind varchar2(32);
   v_err number(10,0);
   v_sou varchar2(10);
   v_curtime date;
begin
   v_kind := i_kind ;
   v_curtime := sysdate ;

   --获取基础信息
   with t_sp as --主表信息
         (select sp_id,'sp' sou,
                 'SP主表信息：{名称=['||spname||'],主表状态=['||flag||'],前置源=['||need_sou||
                 '],剩余次数=['||retry_cnt||'],运行耗时=['||runtime||'],任务组=['||task_group||'],参数组=['||param_sou||']}' remark
            from vw_imp_sp
           where bvalid = 1
           union all
          select tid,'etl',
                 'ETL主表信息：{名称=['||spname||'],源表=['||sou_db_conn||':'||sou_owner||'.'||sou_tablename||
                 '],主表状态=['||flag||'],剩余次数=['||retry_cnt||'],运行耗时=['||runtime||'],参数组=['||param_sou||']}'
            from vw_imp_etl
           where bvalid = 1
           union all
          select pn_id,'plan',
                 'PLAN主表信息：{名称=['||spname||'],主表状态=['||flag||'],运行耗时=['||runtime||']}'
            from vw_imp_plan
           union all
          select ds_id,'ds',
                 'DS主表信息：{名称=['||ds_name||'],主表状态=['||flag||'],剩余次数=['||retry_cnt||'],运行耗时=['||runtime||'],参数组=['||param_sou||']}'
            from vw_imp_ds2),
         t_com as --附属表信息
          (select sp_id,com_id,flag,'子表信息：{命令类型=['||com_kind||'],命令顺序=['||com_idx||'],命令状态=['||flag||']}' remark
             from tb_imp_sp_com
            union all
           select ds_id,tbl_id,flag,'子表信息：{状态=['||flag||'],目标表=['||dest_tablename||']}'
             from tb_imp_ds2_tbls)
   select max(t.remark||case when length(i_kind)=2 then chr(10)||b.remark else '' end),
          nvl(sum(case when nvl(b.flag,'N')='Y' then 0 else 1 end),-1),
          max(sou)
          into v_remark,v_err,v_sou
     from t_sp t
    inner join t_com b on b.sp_id = t.sp_id and i_sp_id in(b.sp_id,b.com_id) and nvl(b.flag,'N')<>'X'
    where length(i_sp_id) = 32 ;

   --找到对应的主表信息(v_err=-1表示未找到对应记录,0表示正确,大于1表示有错误任务)
   if v_err >= 0 then
     v_kind := case when i_kind = 'Y' and v_err > 0  then 'E' else i_kind end ;
     if length(v_kind) = 1 then
       --主表的状态变更（1位字符）
       --SP计算
       update tb_imp_sp
          set flag       = v_kind,
              start_time = decode(v_kind,'R',v_curtime,start_time),
              end_time   = decode(v_kind,'Y',v_curtime,'E',v_curtime,end_time),
              runtime    = decode(v_kind,'Y',(v_curtime-start_time)*24*60*60,'E',runtime/2,runtime),
              retry_cnt  = retry_cnt - decode(v_kind,'E',1,0)
        where sp_id = i_sp_id and v_sou = 'sp' ;
       --ODS采集
       update tb_imp_etl
          set flag       = v_kind,
              start_time = decode(v_kind,'R',v_curtime,start_time),
              end_time   = case when v_kind in('E','Y') then v_curtime else end_time end,
              runtime    = decode(v_kind,'Y',(v_curtime-start_time)*24*60*60,'E',runtime/2,runtime),
              retry_cnt  = retry_cnt - decode(v_kind,'E',1,0)
        where tid = i_sp_id and v_sou = 'etl' ;
       --计划任务
       update tb_imp_plan
          set flag       = v_kind,
              start_time = decode(v_kind,'R',v_curtime,start_time),
              end_time   = case when v_kind in('E','Y') then v_curtime else end_time end,
              runtime    = (v_curtime-start_time)*24*60*60
        where pn_id = i_sp_id and v_sou = 'plan' ;
       --数据服务
       update tb_imp_ds2
          set flag       = v_kind,
              start_time = decode(v_kind,'R',v_curtime,start_time),
              end_time   = case when v_kind in('E','Y') then v_curtime else end_time end,
              runtime    = decode(v_kind,'Y',(v_curtime-start_time)*24*60*60,'E',runtime/2,runtime),
              retry_cnt  = retry_cnt - decode(v_kind,'E',1,0),
              bupdate    = decode(v_kind,'E','Y',bupdate)
        where ds_id = i_sp_id and v_sou = 'ds' ;

       if v_kind = 'R' then
         --主表开始执行,附属表状态置为N
         update tb_imp_sp_com set flag='N'
          where flag<>'X' and sp_id = i_sp_id and v_sou in ('sp','etl','plan') ;

         --ds_etl:数据服务开始执行，推送列表状态置为N(重跑时仅报错任务置N)
         update tb_imp_ds2_tbls
            set flag = 'N'
          where ds_id = i_sp_id and v_sou = 'ds'
            and (
                (nvl(flag,'N') <> 'X' and (select retry_cnt from tb_imp_ds2 where ds_id = i_sp_id) = 3)
                or
                (nvl(flag,'E') in('E','R') and (select retry_cnt from tb_imp_ds2 where ds_id = i_sp_id) < 3)
                );

       elsif v_kind = 'E' then
         --任务执行结束,报错提醒
         for c1 in(select v_sou||':'||spname || '执行失败!!' || chr(10) || '[' ||
                            to_char(start_time, 'YYYY-MM-DD HH24:MI:SS') || '=>' || to_char(end_time, 'HH24:MI:SS') || ']'||
                            case when v_sou in('ds','plan') then msg2 end msg,
                          mobile
                     from (--sp执行结束
                           select sp_id, t.spname, start_time, end_time, a.proj_name||',1' mobile
                             from vw_imp_sp t
                             left join vw_ci_deploy a on a.spname=t.sp_owner||'.'||t.sp_name and a.bvalid = 1
                            where t.sp_id = i_sp_id and v_sou = 'sp' and t.retry_cnt = 0 and t.flag = 'E'
                            union all
                           --数据服务执行结束
                           select ds_id, ds_name, start_time, end_time, '1'
                             from vw_imp_ds2
                            where ds_id = i_sp_id and v_sou = 'ds' and retry_cnt = 0 and flag = 'E'
                            union all
                           --计划任务执行结束
                           select pn_id, spname, start_time, end_time, '1'
                             from vw_imp_plan
                            where pn_id = i_sp_id and v_sou = 'plan' and flag = 'E') t
                     left join (select ds_id,
                                       chr(10)||'失败任务'||sum(case when flag not in('Y','X') then 1 else 0 end)||'个(总任务'||count(1)||'个)' msg2
                                  from (
                                   select ds_id,nvl(flag,'E') flag,dest_tablename
                                     from tb_imp_ds2_tbls
                                    where ds_id = i_sp_id and v_sou = 'ds'
                                    union all
                                   select sp_id,nvl(flag,'E'),
                                          replace(to_char(regexp_substr(com_text,'^[^'||chr(10)||':,]+')),'#')
                                     from tb_imp_sp_com
                                    where sp_id = i_sp_id and v_sou = 'plan'
                                    ) group by ds_id
                               ) a on a.ds_id = t.sp_id )
         loop
             sp_sms(c1.msg,c1.mobile,'110') ;
         end loop ;

       end if ;

     else
       --附属表的状态变更（2位字符）
       update tb_imp_sp_com
          set flag       = substr(v_kind,2,1),
              start_time = decode(v_kind,'cR',v_curtime,start_time),
              end_time   = decode(v_kind,'cY',v_curtime,'cE',v_curtime,end_time)
        where com_id = i_sp_id and flag <> 'X' and v_sou in ('sp','etl','plan') ;

       update tb_imp_ds2_tbls
          set flag       = substr(v_kind,2,1),
              start_time = decode(v_kind,'cR',v_curtime,start_time),
              end_time   = decode(v_kind,'cY',v_curtime,'cE',v_curtime,end_time)
        where tbl_id = i_sp_id and flag <> 'X' and v_sou = 'ds' ;
     end if ;

     --记录操作流水
     insert into tb_imp_jour(kind,trade_date,status,key_id,remark)
     values(v_sou,gettd(),v_kind,i_sp_id,
            v_remark||chr(10)||'开始时间：'||to_char(v_curtime,'YYYYMMDD HH24:MI:SS')||',执行耗时：'||to_char(trunc((sysdate-v_curtime)*24*60*60),'fm99999999')||
            '秒==>传入参数：{i_kind=['||i_kind||'],i_sp_id=['||i_sp_id||']}<==');
     commit;

   end if ;

exception
   when others then
        sp_sms('sp_imp_status执行报错,i_kind=['||i_kind||'],i_sp_id=['||i_sp_id||'],v_sou=['||v_sou||'],v_kind=['||v_kind||'],错误说明=['||sqlerrm||']','18692206867','110') ;
end;