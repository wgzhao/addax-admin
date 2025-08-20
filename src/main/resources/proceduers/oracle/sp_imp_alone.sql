CREATE OR REPLACE procedure sp_imp_alone(i_kind in varchar2,i_sp_id in varchar2:='',i_value1 in varchar2:='')
as
   v_tradedate number(10,0);
   v_remark varchar2(2000);
   v_err number(10,0);
   v_sp_id varchar2(4000);
   strtmp varchar2(1000);
   ctmp clob;
   v_curtime date;
begin
   v_sp_id := i_sp_id ;
   v_remark := '' ;
   v_tradedate := gettd() ;
   v_curtime := sysdate ;

   --符合条件判断开始
   if i_kind = 'plan_start' then
      --盘后采集
      for c1 in (--盘后定时采集
                 select sysid from vw_imp_system
                  where sys_kind='etl'
                    and bvalid=1
                    and fn_imp_pntype(db_start_type)=1
                    and to_char(v_curtime, 'HH24MI') not between '0001' and '0023'
                    and fn_imp_timechk(v_curtime,db_start) = 1
                    and i_sp_id is null
                  union all
                 --手工指定数据源采集
                 select sysid from vw_imp_system
                  where sys_kind = 'etl' and sysid = i_sp_id and i_sp_id is not null
                  union all
                 --已经完成采集前初始化的判断标志数据源
                 select fid from tb_imp_flag
                  where tradedate = v_tradedate and kind = 'ETL_START' and fval = 2 and i_sp_id is null )
      loop
          --数据源采集任务状态置为N
          update tb_imp_etl
             set flag      = (case when sou_sysid='ZD' and v_tradedate<>getparam('CW1') and sou_tablename like 'qt%' then 'X' else 'N' end),
                 runtime   = runtime*power(2,3-retry_cnt),
                 retry_cnt = 3,
                 etl_kind  = 'A'
           where flag not in('R','W')
             and (flag <> 'X' or sou_sysid = 'ZD')
             and sou_sysid = c1.sysid ;

          --检查数据源下是否有正在执行的采集
          select case when count(1)>0 then
                      chr(10) || '警告:存在'|| count(1) ||'个正在采集的任务!![' || to_char(substr(wm_concat(sou_tablename),1,350)) || ']' end
                 into v_remark
            from tb_imp_etl
           where flag = 'R' and sou_sysid = c1.sysid ;

          --检查数据源是否重复执行
          select case when count(1)>0 then
                      chr(10) || '重复执行:上一次'||decode(max(fval),4,'成功',5,'失败','未结束')
                              || ',时间['||max(decode(fval,3,to_char(dw_clt_date,'YYYY-MM-DD HH24:MI:SS')))||
                                 '=>'||max(case when fval<>3 then to_char(dw_clt_date,'HH24:MI:SS') end)||']' end
                 into strtmp
            from tb_imp_flag
           where tradedate = v_tradedate and kind in('ETL_START','ETL_END') and fid = c1.sysid and fval in(3,4,5) ;
          v_remark := v_remark || strtmp ;

          --数据源开始采集提醒(判断标志,重复执行,正在执行 的数据源需要短信提醒)
          select case when v_remark is not null or count(1)=1 then '110' else '000' end
                 into strtmp
            from vw_imp_etl_judge
           where px = 1 and sysid = c1.sysid ;
          sp_sms( '数据源[' || to_char(fn_imp_value('sysname',c1.sysid)) || ']正式开始采集!!' || v_remark , '1' , strtmp ) ;

          --数据源标志处理:只保留开始标志
          sp_imp_flag('del','ETL_START,ETL_END,TASK_GROUP',c1.sysid);
          sp_imp_flag('add','ETL_START',c1.sysid,3);

          v_sp_id := v_sp_id || c1.sysid || ',' ;
      end loop;

      --判断标志符合采集条件,可以执行采集前置任务
      for c1 in(select sysid,sys_name
                from vw_imp_etl_judge
                where bstart=-1 and px<=2 and i_sp_id is null
                group by sysid,sys_name
                having max(case when px=1 and fval=1 then 1 else 0 end)=1 and
                       min(case when px=2 and fval=1 then 0 else 1 end)=1 )
      loop
        sp_imp_flag('add','ETL_START',c1.sysid,0) ;
        sp_sms( '判断标志的数据源[' || c1.sysid||'_'||c1.sys_name || ']已经符合采集条件' ) ;
        v_sp_id := v_sp_id || c1.sysid || ',' ;
      end loop ;

      --无前置,按计划调起的ODS采集,SP计算及数据服务
      --盘后任务重新采集
      update tb_imp_etl
         set flag='N',retry_cnt=3,etl_kind='A'
       where flag not in('R','X')
         and fn_imp_timechk(v_curtime, after_retry_fixed) = 1
         and fn_imp_pntype(after_retry_pntype) = 1 ;
      --计划调起数据服务
      update tb_imp_ds2
         set flag='N',retry_cnt=3
       where flag not in('R','X')
         and fn_imp_timechk(v_curtime, pn_fixed, pn_interval, pn_range) = 1
         and fn_imp_pntype(pn_type) = 1
         and (fn_imp_freqchk(run_freq) = 1 or run_freq = 'D') ;
      --计划调起SP计算
      update tb_imp_sp
         set flag='N',retry_cnt=3
       where flag not in('R','X')
         and fn_imp_timechk(v_curtime, pn_fixed, pn_interval, pn_range) = 1
         and fn_imp_pntype(pn_type) = 1
         and (fn_imp_freqchk(run_freq) = 1 or run_freq = 'D') ;

      --实时采集:交易日当天8:00-23:00,或传入的指定任务组
      if to_char(v_curtime, 'HH24MI') between '0800' and '2300' and to_char(v_curtime, 'YYYYMMDD') in (gettd(),getntd()) then
        update tb_imp_etl t
           set flag = 'N', retry_cnt = 3, etl_kind = 'R'
         where (
               fn_imp_timechk(v_curtime,realtime_fixed,realtime_interval,realtime_interval_range) = 1 or
               (realtime_taskgroup is not null and instr(','||realtime_taskgroup||',' , ','||nvl(i_sp_id,'0')||',')>0)
               )
           and flag in ('E', 'Y', 'N');

        --删除符合本次实时采集表的任务组完成标志
        delete tb_imp_flag
         where tradedate = v_tradedate
           and kind = 'TASK_GROUP'
           and fid in (select a.task_group from tb_imp_etl t
                        inner join vw_imp_taskgroup a
                           on instr(','||t.realtime_taskgroup||',' , ','||a.task_group||',')>0
                        where t.realtime_taskgroup is not null and t.flag <> 'X'
                        group by a.task_group
                       having count(1) = sum(case when etl_kind = 'R' and flag = 'N' then 1 else 0 end) );
      end if ;

   --符合条件的任务开始执行
   elsif i_kind = 'sp_start' then
      --符合执行条件的任务组
       for c1 in(select a.task_group
                from (
                  select task_group from vw_imp_taskgroup_over  --常规任务组
                  union all
                  select task_group from vw_imp_ds2_needs where bmulti=1 group by task_group having sum(bover)=count(1) --组合任务组（数据源+任务组的模式）
                ) a
                  --剔除已经执行过的任务组
                  left join (select fid
                               from tb_imp_flag
                              where tradedate = v_tradedate
                                and kind = 'TASK_GROUP'
                              group by fid) b
                    on b.fid = a.task_group
                 where b.fid is null
                 group by a.task_group )
      loop
        --数据服务
        update tb_imp_ds2 set flag = 'N', retry_cnt = 3
         where task_group = c1.task_group and flag in('Y','E') and fn_imp_freqchk(run_freq)=1;
        --SP计算
        update tb_imp_sp set flag = 'N' , retry_cnt = 3 , runtime = runtime * power(2,3-retry_cnt)
         where realtime_taskgroup = c1.task_group and flag in('Y','E') and fn_imp_freqchk(run_freq)=1;
        --写任务组完成标志
        insert into tb_imp_flag(tradedate,kind,fid,fval) values ( v_tradedate , 'TASK_GROUP' , c1.task_group , 1 ) ;
        v_sp_id := v_sp_id || c1.task_group || ',' ;
      end loop ;

      --符合执行条件的sp
      update tb_imp_sp set flag = 'N' , retry_cnt = 3 , runtime = runtime * power(2,3-retry_cnt)
      where flag<>'R'
        and sp_id in(select sp_id from vw_imp_sp_needs group by sp_id
                     having count(1) = sum(case when needs_flag = 'Y' then 1 else 0 end)
                        and sum(case when needs_end_time > nvl(start_time, needs_end_time - 1) then 1 else 0 end) > 0 ) ;
      --SP及前置SP均为N的，当前SP临时置为Y
      --前置SP准备执行，但是下游的SP正在执行，前置SP临时置为Y
      update tb_imp_sp set flag='Y'
       where sp_id in(select sp_id from vw_imp_sp_needs where flag='N' and needs_flag='N'
                      union all
                      select needs from vw_imp_sp_needs where flag='R' and needs_flag='N' ) ;
      --重复执行的sp，删除任务组的完成标志
      delete from tb_imp_flag
       where tradedate = v_tradedate and kind = 'TASK_GROUP'
         and instr( (select ','||wm_concat(task_group)||',' from (select task_group from tb_imp_sp where flag='N' and task_group is not null group by task_group) ) , ','||fid||',' ) >0 ;

   --数据源采集结束
   elsif i_kind = 'etl_end' then
      select '总数:'||ncnt||'张==>{成功:'||nok||'张'||
             case when nerr>0 then ',错误:'||nerr||'张' else '' end ||'}'||chr(10)||
             '耗时:'||runtime||'秒'||
             case when nerr>0 then chr(10)||'错误的表:{'||to_char(substr(err_tbls,1,300))||'}' else '' end,
             nvl(nerr,0)
             into v_remark,v_err
      from (
      select sum(case when flag='E' then 1 else 0 end) nerr,
             sum(case when flag='Y' then 1 else 0 end) nok,
             count(1) ncnt,
             trunc((max(end_time)-min(start_time))*24*60*60) runtime,
             wm_concat(case when flag='E' then dest_tablename end) err_tbls
      from vw_imp_etl
      where sysid = i_sp_id and bvalid = 1 ) ;

      select case when v_err>0 or count(1)=1 then '110' else '000' end
             into strtmp
      from vw_imp_etl_judge
      where px = 1 and sysid = i_sp_id ;
      sp_sms( '数据源[' || to_char(fn_imp_value('sysname',i_sp_id)) || ']采集结束'||chr(10)||v_remark ,'UF' , strtmp ) ;
      insert into tb_imp_flag(tradedate,kind,fid,fval) values ( v_tradedate , 'ETL_END' , i_sp_id , decode(v_err , 0 , 4 , 5 ) ) ;

      --防止类型变更为实时采集时，自动调起实时的任务组
      insert into tb_imp_flag(tradedate,kind,fid,fval)
      select v_tradedate, 'TASK_GROUP', a.task_group, 1
        from tb_imp_etl t
       inner join vw_imp_taskgroup a
          on instr(','||t.realtime_taskgroup||',' , ','||a.task_group||',') > 0
        left join tb_imp_flag b
          on b.tradedate = v_tradedate
         and b.kind = 'TASK_GROUP'
         and b.fid = a.task_group
       where t.realtime_taskgroup is not null
         and t.sou_sysid = i_sp_id
         and b.fid is null
       group by a.task_group ;

   --实时与盘后的两个关键时间点任务处理
   elsif i_kind = 'real_after' then
      --实时与盘后采集的用户不一致时,采集前先刷新表结构信息,20210315修改为不关注flag字段
      update tb_imp_etl set bupdate='Y',etl_kind=i_sp_id
       where realtime_sou_owner is not null;
      --有字段变更的,更新表(在调用该任务前,必须调用一次全量的获取表结构)(目前仅在交易日早上更新一次表结构)
      update tb_imp_etl set bupdate='Y'
       where i_sp_id = 'R' and tid in(select tid from vw_imp_tbl_diff_hive union select tid from vw_imp_tbl_diff_mysql) ;

      for c1 in(select '采集模式置为' || i_sp_id || chr(10) || replace(wm_concat(sms), ',', chr(10)) sms
                  from (select sou_sysid || ':' || listagg(dest_tablename || chr(10)) within group(order by dest_tablename) sms
                          from tb_imp_etl
                         where bupdate = 'Y' and etl_kind = i_sp_id
                         group by sou_sysid)
                 union all
                select fn_imp_value('taskname',tid)||'的表有'||count(1)||'个字段类型变更!!!' from vw_imp_tbl_diff_hive
                 where substr(alter_sql,1,300) like '%` change `%' group by tid )
      loop
          sp_sms(substr(c1.sms,1,400),'1','110') ;
      end loop ;

   --更新与建表的过程处理
   elsif i_kind = 'bupdate' then
      --数据源需要重新刷新表结构:Y
      update tb_imp_etl set bupdate='Y'
       where rowid in (select rid from vw_imp_etl where sou_db_conn = i_sp_id and bupdate <> 'Y' and i_value1 = 'Y') ;

      --获取完表结构:n
      update tb_imp_etl set bupdate='n'
       where rowid in (select rid from vw_imp_etl where sou_db_conn = i_sp_id and bupdate = 'Y' and i_value1 = 'n') ;

      --数据源采集失败,主动刷新表结构
      if i_sp_id = 'etl_err' then
        for c1 in (with t_errsys as(select fid, fval, row_number() over(partition by fid order by dw_clt_date desc) px from tb_imp_flag where tradedate = v_tradedate and kind = 'ETL_END')
                   select fid from t_errsys where px = 1 and fval = '5')
        loop
           update tb_imp_etl
              set bupdate = 'Y', flag = 'W', retry_cnt = 3
            where flag = 'E' and sou_sysid = c1.fid ;
           sp_sms(to_char(fn_imp_value('sysname',c1.fid))||'采集失败,尝试自动修复','1','110');
        end loop ;

      --更新表结构:N updt_json
      elsif i_sp_id = 'N' then
        --建表完成,置结束状态
        update tb_imp_etl
           set bcreate = 'N'
         where rowid in (select rid from vw_imp_etl where bcreate = 'Y' and bupdate = 'n' and bvalid = 1 and tid in (select tid from tb_imp_tbl_hdp group by tid));

        --删除本次更新表涉及的命令列表(99,100为自动生成，其他的idx可以自定义)
        delete tb_imp_sp_com
         where com_idx in(99,100)
           and sp_id in(select tid from tb_imp_etl where bupdate = 'n' );
        --99:分区
        insert into tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
        select tid,99,'hive',
               'alter table '||replace(lower(dest),'.','.`')||'` drop if exists partition(logdate=''${dest_part}'');'||chr(10)||
               'alter table '||replace(lower(dest),'.','.`')||'` add if not exists partition(logdate=''${dest_part}'');'
          from vw_imp_etl t
         where bupdate = 'n' ;
        --100:datax采集的json
        insert into tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
        select tid,100,'addax',fn_imp_value('jobfile',tid)
          from tb_imp_etl
         where bupdate = 'n' ;

        --更新结束，状态置为N
        update tb_imp_etl
           set bupdate = 'N',
               flag    = case when flag='W' and bcreate='N' then 'N' else flag end,
               retry_cnt = case when flag='W' and bcreate='N' then 1 else retry_cnt end
         where bupdate = 'n' ;

      --数据服务更新涉及表
      elsif i_sp_id = 'D' then
        v_remark := to_char(fn_imp_value('get_schema')) ;
        delete from tb_imp_sp_needtab where kind='DS' and i_value1 in(sp_id,'all') ;
        for ds in (select ds_id from vw_imp_ds2 where bvalid=1 and (ds_id = i_value1 or i_value1 = 'all'))
        loop
          for c1 in(select regexp_replace(upper(' '||sou_table),'[^A-Z0-9_]{1}(HBASE|CLICKHOUSE|MY_JSC|MY_ZG)\.',' XXX_') coms
                      from vw_imp_ds2_mid where ds_id = ds.ds_id and bvalid = 1 and sou_ishdp = 1 and sou_allsql=0)
          loop
            ctmp := c1.coms ;
            for c2 in 1..500
            loop
              strtmp := trim(regexp_substr(ctmp,' ('||v_remark||')\.[A-Z0-9\_]+',1,1)) ;
              if strtmp is not null then
                insert into tb_imp_sp_needtab(sp_id,table_name,kind)
                select ds.ds_id , strtmp , 'DS'
                from tb_imp_sp_needtab
                where kind = 'DS' and table_name = strtmp and sp_id = ds.ds_id
                having count(1) = 0 ;
              else
                exit ;
              end if;
              ctmp := regexp_replace(ctmp,strtmp||'([^A-Z0-9\_]{1}|$)','#');
            end loop ;
          end loop ;
        end loop ;
      end if ;

   --系统检测:整合原有soutab_chk及其他
   elsif i_kind = 'syschk' then
      --生成所有的检测结果
      delete from tb_imp_chk where chk_kind in(select chk_kind from tb_imp_chk_inf where engine='ini');
      for inf in (select t.*,rowid as rid from tb_imp_chk_inf t where bpntype=1 and engine='ini')
      loop
          update tb_imp_chk_inf set start_time = current_timestamp where rowid = inf.rid ;
          execute immediate 'insert into tb_imp_chk(chk_mobile,chk_sendtype,chk_kind,chk_name,chk_content)' || inf.chk_sql ;
          update tb_imp_chk_inf set end_time = current_timestamp where rowid = inf.rid ;
      end loop ;
      delete from tb_imp_chk where chk_kind not in(select chk_kind from tb_imp_chk_inf);

      --配置了短信发送的检测
      for c1 in (select chk_mobile,chk_sendtype,
                        chk_kind || ':' || xmlagg(xmlparse(content chr(10) || chk_name ||
                          case when cnt > 1 then '[' || cnt || '个]' end wellformed) order by 1).getclobval() chk_content
                   from (select chk_mobile, chk_sendtype, chk_kind, chk_name, count(1) cnt
                           from tb_imp_chk
                          where chk_sendtype is not null
                          group by chk_mobile, chk_sendtype, chk_kind, chk_name)
                  group by chk_mobile, chk_sendtype, chk_kind)
      loop
          sp_sms(c1.chk_content,c1.chk_mobile,c1.chk_sendtype);
      end loop ;

   --获取最新的hadoop表结构(不含视图)
   elsif i_kind = 'get_hdptbls' then
     insert into tmp_imp(pkid)
     select db_name||'.'||tbl_name from (select * from tb_imp_etl_tbls_tmp minus select * from tb_imp_etl_tbls) group by db_name||'.'||tbl_name ;

     delete from tb_imp_etl_tbls where db_name||'.'||tbl_name in(select pkid from tmp_imp) ;
     insert into tb_imp_etl_tbls
     select * from tb_imp_etl_tbls_tmp where db_name||'.'||tbl_name in(select pkid from tmp_imp) ;

   --刷新ODS采集表的源和目标结构,用于字段对比
   elsif i_kind = 'colexch_updt' then
      --本次需要更新的表(如果没有指定更新，则是需要更新全部)
      insert into tmp_imp(pkid)
      select tid from vw_imp_etl where bupdate='n' ;
      select count(1) into v_err from tmp_imp ;

      --hdp
      delete from tb_imp_tbl_hdp where tid in(select pkid from tmp_imp) or v_err=0 or tid not in(select tid from tb_imp_etl);
      insert /*+append nologging*/ into tb_imp_tbl_hdp(tid,hive_owner,hive_tablename,col_name,col_type_full,col_type,col_precision,col_scale,col_idx,tbl_comment,col_comment,cd_id)
      select tid,hive_owner,hive_tablename,col_name,col_type_full,col_type,col_precision,col_scale,col_idx,tbl_comment,col_comment,cd_id from vw_imp_tbl_hdp
      where bupdate='n' or v_err=0 ;
      --sou
      delete from tb_imp_tbl_sou where tid in(select pkid from tmp_imp) or v_err=0 or tid not in(select tid from tb_imp_etl);
      insert /*+append nologging*/ into tb_imp_tbl_sou(tid,sou_db_conn,sou_owner,sou_tablename,column_name_orig,column_name,column_id,data_type,data_length,data_precision,data_scale,tbl_comment,col_comment,dest_type,dest_type_full)
      select tid,sou_db_conn,sou_owner,sou_tablename,column_name_orig,column_name,column_id,data_type,data_length,data_precision,data_scale,tbl_comment,col_comment,dest_type,dest_type_full
      from vw_imp_tbl_sou where bupdate='n' or v_err=0;

   end if ;

   --记录操作流水
   insert into tb_imp_jour(kind,trade_date,status,key_id,remark)
   select 'public',v_tradedate,i_kind,v_sp_id,
          v_remark||chr(10)||'开始时间：'||to_char(v_curtime,'YYYYMMDD HH24:MI:SS')||',执行耗时：'||to_char(trunc((sysdate-v_curtime)*24*60*60),'fm99999999')||
          '秒==>传入参数：{i_kind=['||i_kind||'],i_sp_id=['||i_sp_id||'],i_value1=['||i_value1||'],v_err=['||v_err||']}<=='
     from dual ;
   commit;

exception
   when others then
        sp_sms('sp_imp_alone执行报错,kind=['||i_kind||'],sp_id=['||i_sp_id||'],i_value1='||i_value1||'],错误说明=['||sqlerrm||']','18692206867','110') ;
end;