CREATE OR REPLACE procedure sp_imp_deal(i_kind in varchar2,i_key in varchar2 := '')
as
  strtmp1 varchar2(4000);
  strtmp2 varchar2(4000);
  ctmp1 clob;
  ctmp2 clob;
  ntmp integer;
  v_curtime date;
  sqlengine varchar2(2000);
begin
  v_curtime := sysdate ;
  --目前支持的SQL引擎
  select 'presto|shell|hive|clickhouse|allsql|crmdb|'||to_char(replace(wm_concat(dbs),',','|')) into sqlengine
  from (
    select case db_kind_full when 'mysql' then 'my' else 'ora' end||'_('||wm_concat(sysid)||')' dbs from vw_imp_system_allsql
    where db_kind_full in('mysql','oracle')
    group by db_kind_full) ;

  if i_kind = 'git_deploy' and length(nvl(i_key,' '))=32 then
    insert into tmp_ci_deploy_add
      with t_cur as --本次情况
       (select lower(file_name) file_name, file_md5, length(file_content) file_length
          from tb_ci_deploy
         where dep_id = i_key),
      t_last as --上次情况
       (select lower(file_name) file_name, file_md5, length(file_content) file_length
          from tb_ci_deploy
         where dep_id = (select last_dep_id from vw_ci_deploy_id where dep_id = i_key)),
      t_change as --脚本比对情况: 0, '更新', 1, '新增', 3, '置死'
       (select nvl(t.file_name, a.file_name) file_name,
               case
                 when t.file_md5 <> a.file_md5 or t.file_length <> a.file_length then
                  0 --更新
                 when a.file_name is null then
                  1 --新增
                 when t.file_name is null then
                  3 --置死
                 else
                  9
               end add_kind
          from t_cur t
          full join t_last a
            on a.file_name = t.file_name)
      --对脚本做预处理
      select nvl(a.sp_id, sys_guid()) sp_id,
             nvl(a.sp_owner, regexp_substr(x.file_name, '^[^\.]+')) sp_owner,
             nvl(a.sp_name,
                 regexp_substr(regexp_replace(x.file_name, '\.sql$'),
                               '[^\.]+$')) sp_name,
             case
               --when a.flag = 'X' and x.add_kind <= 1 then
               -- 2 --复活
               when a.sp_id is null and x.add_kind = 0 then
                1
               when a.sp_id is not null and x.add_kind = 1 then
                0
               else
                x.add_kind
             end add_kind,
             replace(
              case when not regexp_like(file_content,'^\s*\-{4}('||sqlengine||')\-{4}' || chr(10))
                   then '----presto----' || chr(10) || file_content --处理代码前面没有写引擎的，用默认引擎
              else file_content end
             ,chr(9),'    ') com_text --TAB改为四个空格，解决前台页面不显示TAB的问题
        from t_change x
        left join tb_ci_deploy t
          on t.dep_id = i_key
         and x.file_name = lower(t.file_name)
        left join tb_imp_sp a
          on a.sp_owner || '.' || a.sp_name = regexp_replace(x.file_name, '\.sql$')
       where x.add_kind < 9
         and regexp_like(x.file_name, '^[a-z0-9_]+\.[a-z0-9_]+\.sql$') ;

    --新增脚本插入配置主表
    insert into tb_imp_sp(sp_id,sp_owner,sp_name,task_group)
    select sp_id,sp_owner,sp_name,
           case when sp_owner in('sjzl','qmfx') then sp_owner end task_group
      from tmp_ci_deploy_add
     where add_kind = 1 ;

    --复活脚本
    --update tb_imp_sp set flag='Y',start_time=sysdate
    --where sp_id in(select sp_id from tmp_ci_deploy_add where add_kind = 2) ;

    --置死脚本
    update tb_imp_sp set flag='X'
    where sp_id in(select sp_id from tmp_ci_deploy_add where add_kind = 3) ;

    --将脚本插入命令列表
    delete from tb_imp_sp_com where sp_id in(select sp_id from tmp_ci_deploy_add);
    for c1 in(select sp_id,com_text||chr(10)||'----shell----'||chr(10) com_text,regexp_count(nvl(com_text,' '),'\-{4}('||sqlengine||')\-{4}'||chr(10)) com_num from tmp_ci_deploy_add)
    loop
        --避免一个项目中不同目录出现重复名称
        delete from tb_imp_sp_com where sp_id = c1.sp_id ;
        ctmp1 := c1.com_text ;
        --如果脚本内容注明了分段,则分段插入命令明细表
        for c2 in 1..c1.com_num
        loop
              --获取当前分段标志
              strtmp1 := regexp_substr(ctmp1 , '\-{4}('||sqlengine||')\-{4}'||chr(10) , 1 , 1 ) ;
              --删除当前分段标志整行
              ctmp1 := regexp_replace(ctmp1 , '^\s*\-{4}('||sqlengine||')\-{4}'||chr(10) ) ;
              --计算下一个分段标志的位置,用于截取代码
              ntmp := instr(ctmp1, regexp_substr(ctmp1,'\-{4}('||sqlengine||')\-{4}'||chr(10) , 1 , 1 ) ) ;
              --将当前分段代码插入命令明细表(代码少于5个字符的，认定为无效，无需插入命令表)
              ctmp2 := substr(ctmp1 , 1 , ntmp-1) ;
              insert into tb_imp_sp_com(sp_id,com_idx,com_kind,com_text)
              --values(c1.sp_id , c2*10 , regexp_substr(strtmp1,'[a-z]+') , substr(ctmp1 , 1 , ntmp-1) );
              select c1.sp_id , c2*10 , regexp_substr(strtmp1,'[a-z_]+') , ctmp2
              from dual where length(ctmp2)>5 ;
              --删除当前分段代码
              ctmp1 := substr(ctmp1 , ntmp , length(ctmp1) );
        end loop ;
    end loop;
    dbms_output.put_line('1');

    --hadoop中所有在用的schema及sp_owner
    strtmp2 := fn_imp_value('get_schema');

    --将需要处理的表，对代码部分进行预处理
    insert into tmp_imp_sp_needtab (sp_id, sp_name, com_text)
    select t.sp_id,
           upper(t.sp_owner || '.' || t.sp_name) sp_name,
           regexp_replace(replace(
            regexp_replace(
            regexp_replace(regexp_replace(regexp_replace(
		        replace(regexp_replace(regexp_replace(
            replace(replace(replace(upper(com_text),'--NEED'),'/*',chr(1)),'*/',chr(2)), --替换特殊字符
               chr(1)||'[^'||chr(2)||']*'||chr(2),' '), --删除/* */的注释
               '--.*'),chr(10),' '),  --删除--的注释
               '\s*\.\s*' , '.'),'[^0-9A-Z_]{1}' ||upper(t.sp_owner || '\.' || t.sp_name) || '[^0-9A-Z_]{1}', ' # '),'''[^'']*''',''),
               '(INSERT\s+(INTO|OVERWRITE\s+TABLE)\s+|CREATE(\s+|\s+OR\s+REPLACE\s+)VIEW\s+)','INSERT INTO '),
			   'HIVE.'),' (CLICKHOUSE|CRMDB|MY_RTDB|CRM_DATA)\.',' XXX_') com_text
      from tmp_ci_deploy_add t;

    --删除本次新增SP的涉及表及目标表
    delete from tb_imp_sp_needtab
     where kind in('ALL','DEST')
       and sp_id in(select sp_id from tmp_imp_sp_needtab) ;

    --得到脚本中涉及自身之外的其他所有表
    for c1 in (select * from tmp_imp_sp_needtab )
    loop
              ctmp1 := c1.com_text ;
              for idx in 1..100
              loop
                  strtmp1 := to_char(regexp_substr(ctmp1,'('||strtmp2||')\.[A-Z0-9_]+',1,1,'c')) ;
                  if strtmp1 is not null then
                      --将找到的表插入前置表,不包含SP名字自身
                      insert into tb_imp_sp_needtab(sp_id,table_name,kind)
                      select c1.sp_id,strtmp1,'ALL' from tb_imp_sp_needtab
                      where kind='ALL' and sp_id = c1.sp_id and table_name = strtmp1
                      having count(1)=0 and strtmp1 <> c1.sp_name;
                      --将找到的表，从源代码中删除，避免再次匹配上,也避免前置表中出现重复的匹配表
                      ctmp1 := regexp_replace(regexp_replace(' '||ctmp1||' ','[^A-Z0-9_]{1}'||replace(strtmp1,'.','\.')||'[^A-Z0-9_]{1}','#'),'^[^#]*#');
                  else
                      exit;
                  end if;
              end loop;
    end loop ;
    dbms_output.put_line('2');

    --得到脚本中除自身和edw_check_log之外的所有目标表
    for c1 in(select * from tmp_imp_sp_needtab where com_text like '%INSERT INTO%')
    loop
              ctmp1 := c1.com_text ;
              for idx in 1..100
              loop
                  strtmp1 := to_char(regexp_replace(regexp_substr(ctmp1,'INSERT INTO [0-9A-Z_]+\.[0-9A-Z_]+',1,1),'INSERT INTO '));
                  if strtmp1 is not null then
                      insert into tb_imp_sp_needtab(sp_id,table_name,kind)
                      select c1.sp_id,strtmp1,'DEST' from tb_imp_sp_needtab
                      where kind='DEST' and sp_id = c1.sp_id and table_name = strtmp1
                      having count(1)=0 and strtmp1 <> c1.sp_name ;
                      ctmp1 := regexp_replace(regexp_replace(ctmp1,'INSERT INTO '||strtmp1||'[^A-Z0-9_]{1}','#'),'^[^#]*#');
                  else
                      exit ;
                  end if ;
              end loop;
    end loop;
    dbms_output.put_line('3');

    --计算出SP涉及的所有前置(基于SP计算最近一层的依赖)
    delete from tb_imp_sp_needtab where kind='NEEDS';
    insert into tb_imp_sp_needtab
      (sp_id, table_name, kind)
      select sp_id, needs_sp_id, 'NEEDS'
        from (select t.sp_id, a.sp_id needs_sp_id
                from tb_imp_sp_needtab t
               inner join vw_imp_sp a
                  on upper(a.sp_owner || '.' || a.sp_name) = t.table_name
                 and a.sp_id <> t.sp_id
               where t.kind = 'ALL'
              union
              select t.sp_id, a.sp_id
                from tb_imp_sp_needtab t
               inner join tb_imp_sp_needtab a
                  on a.table_name = t.table_name
                 and a.sp_id <> t.sp_id
                 and a.kind = 'DEST'
               where t.kind = 'ALL'
              union
              select t.sp_id, nvl(a.need_sou, 'UF') need_sou
                from vw_imp_sp t
                left join (select sp_id, substr(table_name, 4, 2) need_sou
                             from tb_imp_sp_needtab
                            where kind = 'ALL'
                              and table_name like 'ODS%') a
                  on a.sp_id = t.sp_id) ;

    --SP的计算层级，层级之内并行，层级之间串行
    delete from tb_imp_sp_needtab where regexp_like(kind,'RUNLEV\d+') ;
    ----只依赖数据源的SP，即为初始层
    insert into tb_imp_sp_needtab(sp_id,kind)
    select sp_id,'RUNLEV0' from vw_imp_sp where bvalid=1 and sp_id not in(select sp_id from vw_imp_sp_needs where length(needs)=32);

    ----其他层级
    for c1 in 1..20
    loop
      insert into tb_imp_sp_needtab(sp_id,kind)
      select t.sp_id,'RUNLEV'||c1
        from vw_imp_sp_needs t
        left join tb_imp_sp_needtab a on a.sp_id=t.needs and regexp_like(a.kind,'RUNLEV\d+')
       where length(t.needs)=32 and t.sp_id not in(select sp_id from tb_imp_sp_needtab where regexp_like(kind,'RUNLEV\d+'))
       group by t.sp_id
      having count(1)=sum(case when a.sp_id is not null then 1 else 0 end);
    end loop ;

    --SP穿透后的依赖
    delete from tb_imp_sp_needtab where kind='NDS';
    insert into tb_imp_sp_needtab(kind,sp_id,table_name)
    select 'NDS',sp_id,needs from vw_imp_sp_needs
    where length(needs)=32 and sp_id in(select sp_id from tb_imp_sp_needtab where kind like 'RUNLEV%');

    for c1 in 1..10
    loop
      insert into tb_imp_sp_needtab(kind,sp_id,table_name)
      select distinct kind,t.sp_id,a.needs from tb_imp_sp_needtab t
      inner join vw_imp_sp_needs a on a.sp_id=t.table_name and length(a.needs)=32
      where kind = 'NDS' and not exists(select 1 from tb_imp_sp_needtab where kind='NDS' and sp_id=t.sp_id and table_name=a.needs);
    end loop ;

    --更新SP依赖显示表，仅影响前台页面展示，具体的调起依赖，直接使用的源表
    delete from tb_imp_sp_needall ;
    insert into tb_imp_sp_needall
    with t_sp as
     (select distinct sp_id, table_name, kind
        from tb_imp_sp_needtab t
       where t.kind in ('ALL', 'NEEDS', 'DEST' , 'NDS'))
    select t.sp_id,
           --SP直接的依赖（贴源层）
           regexp_replace(listagg(case
                                    when kind = 'NEEDS' and length(table_name) = 2 then
                                     table_name || ','
                                  end) within group(order by table_name),
                          ',$') need_sou,
           listagg(case
                     when kind = 'NEEDS' and length(table_name) = 32 then
                      a.spname || chr(10)
                   end) within group(order by a.spname) need_sp,
           listagg(case
                     when kind = 'ALL' then
                      table_name || chr(10)
                   end) within group(order by a.spname) sp_alltabs,
           listagg(case
                     when kind = 'DEST' then
                      table_name || chr(10)
                   end) within group(order by a.spname) sp_dest,
           --SP穿透后的全部依赖
           regexp_replace(listagg(case
                                    when kind = 'NDS' and length(table_name) = 2 then
                                     table_name || ','
                                  end) within group(order by table_name),
                          ',$') through_need_sou,
           replace(wm_concat(case when kind = 'NDS' and length(table_name) = 32 then a.spname end),',',chr(10)) through_need_sp
      from t_sp t
      left join (select sp_id, sp_owner || '.' || sp_name spname
                   from tb_imp_sp) a
        on a.sp_id = t.table_name
     group by t.sp_id;
     dbms_output.put_line('4');

    --提醒信息
    select 'git发起代码提交!!' || chr(10) ||
           nvl( substr(regexp_replace(xmlagg(xmlparse(content msg || chr(10) wellformed) order by 1).getclobval(), chr(10) || '$'),1,3000) , '没有脚本变更') msg
      into strtmp1
      from (select sp_owner ||
                   decode(add_kind, 0, '更新', 1, '新增', 2, '复活', 3, '置死') ||
                   count(1) || '个:[' || wm_concat(sp_name) || ']' msg
              from tmp_ci_deploy_add
             group by sp_owner, add_kind);

    --计算短信接收人
    select '1,'||wm_concat(proj_name)
           into strtmp2
    from (select proj_name from tb_ci_deploy where dep_id = i_key group by proj_name );

    sp_sms(strtmp1,strtmp2,'010');

  end if ;

  --记录操作流水
  insert into tb_imp_jour(kind,trade_date,status,key_id,remark)
  select 'public',gettd(),i_kind,i_key,
         '开始时间：'||to_char(v_curtime,'YYYYMMDD HH24:MI:SS')||',执行耗时：'||to_char(trunc((sysdate-v_curtime)*24*60*60),'fm99999999')||
         '秒==>传入参数：{i_kind=['||i_kind||'],i_key=['||i_key||']}<=='
    from dual ;
  commit;

exception
   when others then
        sp_sms('sp_imp_deal执行报错,kind=['||i_kind||'],key=['||i_key||'],错误说明=['||substr(sqlerrm,1,200)||']','18692206867','110') ;
end;