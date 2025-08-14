CREATE OR REPLACE function fn_imp_value(i_kind in varchar2,i_sp_id in varchar2:='',i_value1 in varchar2:='')
   return clob
as
   o_return clob;
   ctmp1 clob;
   ctmp2 clob;
   strtmp1 varchar2(4000);
   strtmp2 varchar2(4000);
   v_tradedate int;
begin
   o_return := '' ;
   strtmp1 := '' ;
   strtmp2 := '' ;
   ctmp1 := '' ;
   ctmp2 := '' ;
   v_tradedate := gettd() ;

   --���������������������������
   if i_kind = 'plan_run' then
      with t_sp as
        (--������������:1:plan
         select 'plan|'||pn_id sp_id
         from vw_imp_plan
         where brun=1 and bpntype=1
         --������������������������,������������:2:judge
         union all
         select 'judge|'||decode(bstart,-1,'status_',0,'start_')||sysid
           from vw_imp_etl_judge
          where bstart in(-1,0) and px=1 )
      select replace(wm_concat(sp_id),',',chr(10))
             into o_return
      from t_sp ;

   --���������������������������
   elsif i_kind = 'sp_run' then
   with t_sp as
     (select 'sp' kind, sp_id, 'sp'||sp_owner dest_sys, runtime, brun
        from vw_imp_sp
       where brun = 1 or flag = 'R'
      union all
      select 'etl', tid, sysid, runtime+runtime_add, brun
        from vw_imp_etl
       where brun = 1 or flag = 'R'
      union all
      select 'ds', ds_id, 'ds'||dest_sysid, nvl(runtime,999), brun
        from vw_imp_ds2
       where brun = 1 or flag = 'R')
    select replace(wm_concat(sp_id),',',chr(10))
           into o_return
      from (select decode(kind,'etl','sp',kind)||'|'||sp_id sp_id,
                   brun * row_number() over(order by brun , runtime + 20000 / sys_px desc) px  --������������(������������������������,brun=0),���������������������������
              from (select kind , sp_id , dest_sys , brun , runtime ,
                           row_number() over(partition by kind, dest_sys order by brun , runtime desc) sys_px --������������������������������������������������������������
                      from t_sp)
             where sys_px <= nvl((select db_paral from vw_imp_system
                                   where sysid = dest_sys and sys_kind='etl'),
                                 8))
     where px between 1 and 100 ;

   --COM���������������
   elsif i_kind = 'com_text' then
     select com_text
            into o_return
     from tb_imp_sp_com
     where com_id = i_sp_id ;

     if o_return is not null then
       --���������������(������������������������������������TID)
       select coalesce(a.param_sou,b.param_sou,'C'),b.tid
              into strtmp1,strtmp2
       from tb_imp_sp_com t
       left join tb_imp_sp a on a.sp_id = t.sp_id
       left join tb_imp_etl b on b.tid = t.sp_id
       where t.com_id = i_sp_id ;

       --������������������������������������,SP���������������������
       if strtmp2 is not null then
          select replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(
                 o_return,'${sou_dbcon}',t.sou_db_constr)
                          ,'${sou_user}',t.sou_db_user)
                          ,'${sou_pass}',t.sou_db_pass)
                          ,'${sou_tblname}',case when t.sou_owner like '%-%' then '`'||t.sou_owner||'`' else t.sou_owner end||case when sou_db_kind='sqlserver' and sou_db_conf like '%[soutab_owner:table_catalog]%' then '..' else '.' end||t.sou_tablename)
                          ,'${sou_filter}',replace(replace(t.sou_filter,'\','\\'),'"','\"'))
                          ,'${sou_split}',t.sou_split)

                          ,'${tag_tblname}','/ods/'||lower(replace(t.dest,'.','/'))||'/logdate=${dest_part}')
                          ,'${dest_part}',fn_imp_value('dest_part',tid))
                          ,'${modifier_no}',replace(replace(replace(t.sou_filter,'''',''''''),'\','\\'),'"','\"') )
                          ,'${hdp_cols}',(select regexp_replace(xmlagg(xmlparse(content col_name||',' wellformed) order by col_idx).getclobval(),',$','') from tb_imp_tbl_hdp where tid=t.tid and col_name<>'LOGDATE' group by tid) )
                 into o_return
          from vw_imp_etl t
          where t.tid = strtmp2 ;
       end if ;

       o_return := fn_imp_param_replace( o_return , strtmp1 ) ;
     end if ;

   ---���������������DATAX���job.json������
   --������������������������������������
   elsif i_kind = 'jobfile' then
     select a.jobfile,a.jobkind
            into o_return,strtmp1
     from vw_imp_etl t
     inner join vw_imp_jobfile a on a.jobkind=t.jobkind
     where t.tid = i_sp_id ;

     ctmp1:='';
     ctmp2:='';
     for c1 in (select jobkind,data_type,column_name,bquota,col_name,col_type,col_idx--,dbid
                  from vw_imp_etl_cols
                 where tid = i_sp_id
                 order by col_idx)
     loop
        --������������
        ctmp1 := ctmp1 || '"' ||
                   case upper(c1.col_name)
                     when 'DW_CLT_DATE' then
                      '''${NOW}''' ||case when substr(c1.jobkind,1,1) = 'P' then '::varchar' else '' end
                     when 'MODIFIER_NO' then
                      '''${modifier_no}''' ||case when substr(c1.jobkind,1,1) = 'P' then '::varchar' else '' end
                     when 'DW_TRADE_DATE' then
                      '${TD}' ||case when substr(c1.jobkind,1,1) = 'P' then '::int' else '' end
                     else
                      case --when c1.data_type='BLOB' then 'null'
                           when c1.data_type='ROWID' then 'rowidtochar('||c1.column_name||')'
                           when c1.column_name is null and substr(c1.jobkind,1,1) = 'P' then 'null::varchar' --postgresql�����������������������������������������
                           when c1.bquota = 1 then     --���������������
                                case when substr(c1.jobkind,1,1)='M'
                                     then '`'||c1.column_name||'`'
                                else '\"'||c1.column_name||'\"' end
                      else nvl(c1.column_name, 'null') end
                   end || '",';
        --hadoop���������
        ctmp2 := ctmp2 || case
                  when substr(c1.jobkind, -1) = 'H' then
                   '{"name":"' || lower(c1.col_name) || '","type":"' ||
                   c1.col_type || '"},'
                  when substr(c1.jobkind, 1, 1) = 'H' then
                   '{"index":' || c1.col_idx || ',"type":"' ||
                   case when c1.col_type='decimal' then 'double' when c1.col_type in('varchar','char') then 'string' else c1.col_type end || '"},'
                end;
      end loop;

     select replace(replace(
            o_return,'${sou_col}',regexp_replace(decode(substr(t.jobkind,1,1),'H',ctmp2,ctmp1),',$'))
                     ,'${tag_col}',regexp_replace(decode(substr(t.jobkind,-1),'H',ctmp2,ctmp1),',$'))
            into o_return
     from vw_imp_etl t
     where t.tid = i_sp_id ;

     --dbf--
     if strtmp1 = 'D2H' then
        select listagg('{"index":'||to_char(col_idx)||', "type":"string"}'||',')within group(order by col_idx)||
                       '{"value":"${NOW}","type":"string"},{"value":"ZD","type":"string"},{"value":"${TD}","type":"long"}' cols
               into strtmp1
        from vw_imp_etl_cols t
        where jobkind='D2H' and tid = i_sp_id and col_name not in('dw_clt_date','modifier_no','dw_trade_date') ;
        o_return := replace(o_return,'${col}',strtmp1);
     end if ;

   --������������������������������
   elsif i_kind = 'etl_end' then
      select replace(wm_concat(sysid), ',', chr(10))
             into o_return
      from (select sysid
              from vw_imp_etl
             where bvalid = 1
             group by sysid
            having sum(case when flag = 'Y' or (flag = 'E' and retry_cnt = 0) then 1 else 0 end) = count(1)
               and sum(case when etl_kind='A' then 1 else 0 end) = count(1)
           ) t
      left join (select fid
                   from tb_imp_flag t
                   left join (select sysid, max(end_time) maxtime
                               from vw_imp_etl
                              where bvalid = 1
                              group by sysid) a
                     on a.sysid = t.fid
                  where tradedate = v_tradedate
                    and kind = 'ETL_END'
                    and (fval = '4' or (fval = '5' and dw_clt_date > maxtime))
                  group by fid) a
        on a.fid = t.sysid
     where a.fid is null ;

   --ODS���������������XDS������������
   elsif i_kind = 'etl_end_chk' then
     select entry_content into ctmp1 from tb_dictionary where entry_code='5005' and entry_value='ETL_END_CHK' ;
     for tbl in(
       select t.tid,lower(t.dest) as dest,
              wm_concat(case when regexp_like(a.data_type,'(DATE|TIME)') then 'regexp_replace(' end||
                             case when c.entry_value is not null then '"'||lower(a.column_name)||'"' else lower(a.column_name) end||
                        case when regexp_like(a.data_type,'(DATE|TIME)') then ',''\.[0-9]+$'','''')' end ) as cols
       from vw_imp_etl t
       inner join vw_imp_tbl a on a.tid=t.tid and a.column_name is not null
       left join tb_dictionary c on c.entry_code='2014' and lower(c.entry_value)=lower(a.column_name)
       where t.bvalid = 1 and t.sysid = i_sp_id and t.sysid <> 'JY'
       group by t.tid,t.dest )
     loop
       o_return := o_return || replace(replace(replace(replace(ctmp1,'${dest_part}',fn_imp_value('dest_part_value',tbl.tid)),'${tbl}',tbl.dest),'${otbl}',regexp_replace(tbl.dest,'^ods','xds')),'${cols}',tbl.cols) || chr(10) ;
     end loop ;

   --������logdate���������������
   elsif i_kind = 'dest_part' then
      select case
               when etl_kind = 'R' then
                '0'
               else
                case
                  when dest_part_kind in ('D', 'M', 'Q') then
                   '${T' || dest_part_kind || '}'
                  else
                   '1'
                end
             end
        into o_return
        from vw_imp_etl
       where tid = i_sp_id;

   --������logdate������������
   elsif i_kind = 'dest_part_value' then
      select nvl(a.param_value, '1')
             into o_return
       from vw_imp_etl t
       left join vw_imp_param a
         on a.param_sou = t.param_sou
        and a.param_kind_0 like 'T%'
        and a.param_kind_0 = 'T' || dest_part_kind
      where t.tid = i_sp_id;

   --������������������
   elsif i_kind = 'sysname' then
      select case when i_value1='short' then sys_name else sysid||'_'||sys_name end
             into o_return
      from vw_imp_system
      where sysid = i_sp_id ;

   --������������������
   elsif i_kind = 'taskname' then
      select spname
             into o_return
       from (select spname from vw_imp_etl where tid = i_sp_id
             union all
             select spname from vw_imp_sp where sp_id = i_sp_id
             union all
             select spname from vw_imp_plan where pn_id = i_sp_id
             union all
             select ds_name from vw_imp_ds2_mid where i_sp_id in(ds_id,tbl_id) and rownum=1
             union all
             select sysid||'_'||sys_name from vw_imp_system where sysid = i_sp_id
            );

   --������������������������������
   elsif i_kind = 'pntype_list' then
      select listagg(entry_content||'['||entry_value||']='||fn_imp_pntype(entry_value)||chr(10))within group(order by entry_value)
        into o_return
        from tb_dictionary
       where entry_code='1064' and entry_value<='3';

   --���������������hadoop�����������
   elsif i_kind = 'get_schema' then
      --hadoop������������������schema���sp_owner
      select 'ODS[A-Z0-9]{2}' || listagg('|' || upper(db_name)) within group(order by length(db_name) desc)
             into o_return
       from (select db_name
               from tb_imp_etl_tbls
              where col_idx = 1000
                and db_name not in ('edwuf', 'edwuftp', 'kpiuf', 'kpiuftp', 'tmp', 'default')
                and not regexp_like(db_name , '^(xds|ods)' )
              group by db_name
              union
              select sp_owner from vw_imp_sp where bvalid=1 group by sp_owner) ;

   --������������������������������ds_json,ds_sql,cmd
   --ds_sql:���������������������������������������������������������
   elsif i_kind in('ds_sql','ds_sql_presto','ds_sql_allsql') then
      for c1 in (select 'create or replace view ds.' || t.dsview || ' as ' || chr(10) ||
                        fn_imp_param_replace(regexp_replace(t.sou_table, ';$', '') || ';',
                                                   t.param_sou) ds_sql
                   from vw_imp_ds2_mid t
                  where sou_istab = 0
                    and db_kind_full <> 'file'
                    and sou_allsql=decode(i_kind,'ds_sql_allsql',1,0)
                    and flag = 'N'
                    and ds_id = i_sp_id)
      loop
          o_return := o_return || c1.ds_sql || chr(10) ;
      end loop ;

   --ds_json:���������������datax������JSON���
   elsif i_kind = 'ds_json' then
       with t_map as --���������������������������
          (select distinct col_map,regexp_substr(regexp_substr(col_map, '(as|AS) [^;]+', 1, level), '\w+$') col_map_name,
                           regexp_replace(regexp_substr(';' || col_map || ';' , '[^;]+ (as|AS) \w+;' , 1 , level) , ' (as|AS) \w+;') col_map_define
                      from (select col_map from vw_imp_ds2_mid where col_map is not null and tbl_id = i_sp_id group by col_map)
                   connect by level <= regexp_count(col_map, ';') + 1),
          --hadoop���������������(���������������������������:������������������������������������������,���������������������)
          t_db as (select tbl_id,col_name,col_define,col_type,row_number()over(partition by tbl_id,col_name order by kind desc) px
                  from(select 0 kind, t.tbl_id, col_name, col_name col_define, col_type
                         from vw_imp_ds2_mid t
                        inner join tb_imp_etl_tbls a on decode(t.sou_istab , 1 , lower(t.sou_table) , 'ds.'||t.dsview) = lower(a.db_name||'.'||a.tbl_name)
                        where t.tbl_id = i_sp_id and t.sou_ishdp = '1'
                        union all
                       select 0 kind,x.tbl_id, a.column_name, a.column_name, a.data_type
                         from vw_imp_etl t
                        inner join vw_imp_ds2_mid x on x.tbl_id = i_sp_id and decode(sou_istab , 1 , lower(sou_table) , 'ds.'||dsview) = lower(t.dest) and x.sou_ishdp = '0'
                        inner join tb_imp_etl_soutab a on a.sou_db_conn=t.sou_db_conn and upper(a.owner)=upper(t.sou_owner) and upper(a.table_name)=upper(t.sou_tablename)
                        union all
                       select 1 kind, t.tbl_id, a.col_map_name, a.col_map_define, ' '
                         from vw_imp_ds2_mid t
                        inner join t_map a on a.col_map=t.col_map
                        where t.col_map is not null and t.tbl_id=i_sp_id) ),
          t_sql as (select t.tbl_id,
                           --������������
                           regexp_replace(xmlagg(xmlparse(content
                               case when a.col_define=a.col_name and a.col_type='boolean' and t.db_kind='O'
                                    then 'cast('||a.col_name||' as int),'
                               else a.col_define||',' end wellformed) order by col_name).getclobval(),',$','') s_cols,
                           --���������������
                           regexp_replace(xmlagg(xmlparse(content '"'||
                               case when d.entry_value is not null then
                                    case when t.db_kind = 'M' then '`'||b.column_name||'`'
                                    else '\"'||b.column_name||'\"' end
                               else b.column_name end||'",' wellformed) order by col_name).getclobval(),',$','') d_cols,
                           --������������������
                           case when t.sou_ishdp = 0 then ' from ' ||
                                     max(c.sou_owner || case when c.sou_db_kind='sqlserver' and c.sou_db_conf like '%[soutab_owner:table_catalog]%' then '..' else '.' end || c.sou_tablename) ||
                                     ' where ' || nvl(t.sou_filter,'1=1')
                                when t.sou_istab = 1 then ' from '||t.sou_table||' where '||replace( nvl(t.sou_filter,'logdate=''${dest_part}''') , '${dest_part}' , nvl(to_char(fn_imp_value('dest_part',c.tid)),'${TD}') )
                           else ' from ds.'||max(t.dsview) end s_filter,
                           --������������������
                           max(case when t.sou_allsql=1 then 'jdbc:trino://etl01:18080/hive'
                                    when t.sou_ishdp=1 then 'jdbc:trino://etl01:18080/hive'
                                    else c.sou_db_constr end) s_conn,
                           max(decode(t.sou_ishdp, 1 , 'hive' , c.sou_db_user)) s_user,
                           max(decode(t.sou_ishdp, 1 , '' , c.sou_db_pass)) s_pass
                    from vw_imp_ds2_mid t
                    --���������������������������(hadoop���������������������)
                    inner join t_db a on a.px=1 and a.tbl_id = t.tbl_id
                    --������������������������������
                    inner join tb_imp_etl_soutab b
                       on b.sou_db_conn = t.sou_db_conn
                      and lower(b.owner) = lower(t.dest_owner)
                      and lower(b.table_name) = lower(t.dest_tablename)
                      and lower(b.column_name) = lower(a.col_name)
                    --ODS���������������������������������TID������������������������������������
                    left join vw_imp_etl c on lower(c.dest) = lower(t.sou_table)
                    --������������������
                    left join tb_dictionary d on d.entry_code='2014' and upper(d.entry_value)=upper(b.column_name)
                    where t.tbl_id=i_sp_id
                    group by t.tbl_id,t.sou_table,t.sou_filter,c.tid,t.col_map,t.sou_istab,t.sou_ishdp)
        select replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(to_clob(c.jobfile),'${s_conn}',a.s_conn),'${s_user}',a.s_user),'${s_pass}',a.s_pass),
                   '${s_sql}',fn_imp_param_replace('select '||a.s_cols||a.s_filter,t.param_sou)),
                   '${d_presql}',decode(case when t.retry_cnt<3 and t.pre_sql='T' then 'D' else t.pre_sql end,'T','truncate table @table','D','delete from @table','N','',fn_imp_param_replace(t.pre_sql,t.param_sou))),
                   '${d_postsql}',fn_imp_param_replace(t.post_sql,t.param_sou)), '${d_cols}',a.d_cols),
                   '${d_conn}',d_conn),'${d_user}',d_user),'${d_pass}',d_pass),'${d_tblname}',t.dest_owner||'.'||t.dest_tablename)
               into o_return
        from vw_imp_ds2_mid t
        left join t_sql a on a.tbl_id = t.tbl_id
        left join vw_imp_jobfile c on c.jobkind='H2'||t.db_kind
        where t.tbl_id=i_sp_id ;

   --ds_cmd:���������������������������������
   elsif i_kind = 'ds_cmd' then
       select case when db_kind_full = 'file' then
                 'mkdir -p ' || regexp_replace(d.dest_dir || '/' || d.dest_file, '/[^/]+$') || chr(10) ||
                 '$(rds "get path.bin")/jdbc2console.sh -U "jdbc:trino://nn01:59999/hive" -u "hive" -p "" "' || d.sou_table || '" ' || d.output_format || ' -f Default >' ||
                 d.dest_dir || '/' || d.dest_file
              else
                 '$(rds "get path.bin")/tuna.py -t ' || t.max_runtime || ' -m addax -f ${1}'
              end into o_return
         from vw_imp_ds2_mid t
         left join (select tbl_id,
                           dest_owner dest_dir,
                           to_char(fn_imp_param_replace(dest_tablename,
                                                              param_sou)) dest_file,
                           to_char(fn_imp_param_replace(sou_table, param_sou)) sou_table,
                           pre_sql output_format
                      from vw_imp_ds2_mid t
                     where db_kind_full = 'file'
                       and tbl_id = i_sp_id) d
           on d.tbl_id = t.tbl_id
       where t.tbl_id = i_sp_id ;

   --���������������������
   elsif i_kind = 'ds_needs' then
       select xmlagg(xmlparse(content lower(table_name)||chr(10) wellformed) order by table_name).getclobval()
              into o_return
       from tb_imp_sp_needtab
       where kind='DS' and sp_id = i_sp_id
       group by sp_id ;

   --SP���������������������������SP
   elsif i_kind = 'sp_allnext' then
       for c1 in(
          with t_lev as(select t.*,b.spname,cast(regexp_substr(a.kind,'\d+$') as int) as lev from tb_imp_sp_needtab t
          inner join tb_imp_sp_needtab a on a.sp_id = t.sp_id and a.kind like 'RUNLEV%'
          inner join vw_imp_sp b on b.sp_id = t.sp_id and b.bvalid=1
          where t.kind='NDS' and t.table_name = i_sp_id)
          select table_name,lev,
                 listagg(sp_id||',')within group(order by lev,spname) sp,
                 lev||':'||listagg(spname||',')within group(order by lev,spname) spname
          from t_lev
          group by table_name,lev
          order by lev)
       loop
             ctmp1 := ctmp1 || regexp_replace(c1.sp,',$',chr(10)) ;
             ctmp2 := ctmp2 || regexp_replace(c1.spname,',$',chr(10)) ;
       end loop;
       o_return := ctmp2||case when ctmp1 is not null then chr(10)||'������������������sp������:'||chr(10) end||ctmp1 ;

   --�����������������������������
   elsif i_kind = 'bqxt_flag' then
      for c1 in (select fn_imp_param_replace(replace(regexp_substr(dest_tablename,'/[^/.]+'),'/')||chr(9)||'${TD}'||chr(9)||'1'||chr(9)||'${NOW}',i_value1) flag from tb_imp_ds2_tbls where ds_id = i_sp_id)
      loop
          o_return := o_return || c1.flag || chr(10) ;
      end loop ;
      o_return := regexp_replace(o_return,chr(10)||'$') ;

   --������hadoop��������������������������������������������������������������
   elsif i_kind = 'updt_hive' then
      --������������������,���������������,���������������������
      select replace(wm_concat(create_db), ',', chr(10)) || chr(10)
             into o_return
        from vw_imp_system t
       inner join (select sysid from vw_imp_etl where bcreate = 'Y' and bupdate = 'n' and bvalid = 1 group by sysid) a
          on a.sysid = t.sysid
       where t.sys_kind = 'etl' and t.bvalid = 1
         and t.sysid not in (select fid from tb_imp_flag where tradedate = getltd() and fval = 4 and kind = 'ETL_END');

      ------
      o_return := o_return || chr(10) ;
      ------

      --���������:������������������
      for tbl in (select tid,lower(dest) tblname from vw_imp_etl where bcreate = 'Y' and bupdate = 'n' and bvalid = 1 and tid in(select tid from tb_imp_tbl_sou group by tid))
      loop
          ctmp1 := 'create external table if not exists `' || replace(tbl.tblname,'.','`.`') || '`(' ;
          --���������������(���������������������������,������������2015)
          for col in(select column_name,dest_type_full as column_type,col_comment as column_comment,column_id
                    from tb_imp_tbl_sou t
                    where tid = tbl.tid
                    union all
                    select entry_value,entry_content,remark,10000+row_number()over(order by entry_value)
                    from tb_dictionary where entry_code='2015'
                    order by column_id)
          loop
              ctmp1 := ctmp1 || chr(10) || '  `' || lower(col.column_name || '` ' || col.column_type) ||
                    --��������������
                    case when nvl(col.column_comment,' ')<>' ' then ' comment "' || col.column_comment || '"' end || ',';
          end loop;
          ctmp1 := regexp_replace(ctmp1 , ',$' , ')') ;
          --�����������
          select max(tbl_comment) into strtmp1 from tb_imp_tbl_sou where tid = tbl.tid and tbl_comment is not null ;
          if strtmp1 is not null then ctmp1 := ctmp1 || ' comment "'|| strtmp1 ||'"' ; end if ;
          --������������������������
          ctmp1 := ctmp1 || chr(10) || 'partitioned by(logdate string) stored as orc location ''/ods/'||replace(tbl.tblname,'.','/')||''' tblproperties(''orc.compress''=''lz4'');' ;
          o_return := o_return || ctmp1 || chr(10) ;
      end loop;

      --������������������(hive������)
      for c1 in (select t.alter_sql from vw_imp_tbl_diff_hive t
                  inner join vw_imp_etl a on a.bcreate = 'N' and a.bupdate = 'n' and a.tid=t.tid)
      loop
           o_return := o_return || c1.alter_sql || chr(10) ;
      end loop ;

   --hadoop������������������(���������������������)
   elsif i_kind = 'updt_mysql' then
      for c1 in (select alter_sql from vw_imp_tbl_diff_mysql)
      loop
           o_return := o_return || c1.alter_sql || chr(10) ;
      end loop ;

   --������������������������
   elsif i_kind = 'opstation' then
      o_return := 'delete from edwtp.com_opstation_info_0 where logdate=''' || v_tradedate || ''';' || chr(10) ;
      for c1 in (select 'insert into edwtp.com_opstation_info_0 select '||case when op_entrust_way=1 then 'max(op_entrust_way)' else '''x''' end||',op_station,'''||v_tradedate||
                        ''' from odsuf.'||table_name||' where logdate='''||v_tradedate||''' group by op_station;' cols
                 from (
                 select table_name,
                        max(case when column_name='OP_STATION' then 1 else 0 end) op_station,
                        max(case when column_name='OP_ENTRUST_WAY' then 1 else 0 end) OP_ENTRUST_WAY
                 from tb_imp_etl_soutab
                 where sou_db_conn='DB_UF' and column_name in('OP_ENTRUST_WAY','OP_STATION')
                 group by table_name )
                 where op_station=1
                 order by 1 )
      loop
        o_return := o_return || c1.cols || chr(10) ;
      end loop ;

   --------------------------------------------
   -----------������������������������������-------------
   --------------------------------------------
   elsif i_kind = 'preview_etl' then
      --������������
      select to_char(replace(wm_concat(entry_value),',','|'))
             into strtmp1
      from tb_dictionary where entry_code='2020' ;

      --������presto������������
      for c1 in(select 'select cast(now() as varchar)||'':���������������������'||a.db_name||'.'||a.tbl_name||''';'||
                       'insert into stage_hive.'||a.db_name||'.'||a.tbl_name||
                       ' select '||regexp_replace(xmlagg(xmlparse(content case when a.col_type='string'
                           and regexp_like(upper(a.col_name),'^('||strtmp1||')$') then 'substr("'||a.col_name||'",1,1)||''***'',' else '"'||a.col_name||'",' end wellformed) order by a.col_idx).getclobval(),',$',' from ') ||
                       a.db_name||'.'||a.tbl_name||' where logdate=''' || fn_imp_value('dest_part_value', t.tid) || ''';' hsql
                from vw_imp_etl t
                inner join tb_imp_etl_tbls a on a.db_name=lower(t.dest_owner) and a.tbl_name=lower(t.dest_tablename)
                where t.bpreview='Y' and t.bvalid=1 and t.sysid = i_sp_id
                group by a.db_name,a.tbl_name,t.tid
                order by a.tbl_name )
      loop
                o_return := o_return || c1.hsql || chr(10);
      end loop ;

   --���������������������������������������������
   elsif i_kind = 'preview_presql' then
      --������������������������������
      select t.create_db into o_return
      from vw_imp_system t
      where t.sys_kind='etl' and t.sysid = i_sp_id ;

      --������������������������������
      for c1 in(select 'drop table if exists '||a.db_name||'.'||a.tbl_name||
                       ';create external table if not exists '||a.db_name||'.'||a.tbl_name||' ( '||
                       regexp_replace(xmlagg(xmlparse(content '`'||a.col_name||'` '||a.col_type||',' wellformed) order by a.col_idx).getclobval(),',$',') ')||
                       'partitioned by(logdate string) stored as orc location '''||replace(a.tbl_location,'hdfs://lczq')||''' tblproperties("external.table.purge"="true");'||
                       'msck repair table '||a.db_name||'.'||a.tbl_name||';'||
                       'alter table ' || a.db_name||'.'||a.tbl_name ||' drop if exists partition(logdate=' ||fn_imp_value('dest_part_value', t.tid) || ');' ||
                       'alter table ' || a.db_name||'.'||a.tbl_name ||' add if not exists partition(logdate=' ||fn_imp_value('dest_part_value', t.tid) || ');' ||
                       'alter table ' || a.db_name||'.'||a.tbl_name ||' set tblproperties("external.table.purge"="false");' hsql
                from vw_imp_etl t
                inner join tb_imp_etl_tbls a on a.db_name=lower(t.dest_owner) and a.tbl_name=lower(t.dest_tablename) and a.col_idx<1000
                where bpreview='Y' and t.bvalid=1 and t.sysid = i_sp_id
                group by a.db_name,a.tbl_name,a.tbl_location,t.tid
                order by a.tbl_name )
      loop
               o_return := o_return || chr(10) || c1.hsql ;
      end loop ;

   --���������TDH���������������
   elsif i_kind = 'tdh_pre' then
      for c1 in (select 'drop table if exists ods.'||a.db_name||'_'||a.tbl_name||';'||chr(10)||
                        'create external table if not exists ods.'||a.db_name||'_'||a.tbl_name||' ( '||
                        regexp_replace(xmlagg(xmlparse(content '`'||a.col_name||'` '||a.col_type||',' wellformed) order by a.col_idx).getclobval(),',$',') ')||
                        'partitioned by(etl_date int) stored as orc location ''/inceptor1/user/hive/warehouse/ods.db/add/'||a.db_name||'_'||a.tbl_name||''';'||chr(10)||
                        'alter table ods.' || a.db_name||'_'||a.tbl_name ||' add if not exists partition(etl_date=' ||fn_imp_value('dest_part_value', t.tid) || ');'||chr(10)||
                        'use ods;'||chr(10)||
                        'msck repair table '||a.db_name||'_'||a.tbl_name||';' hsql
                 from vw_imp_etl t
                 inner join tb_imp_etl_tbls a on a.db_name=lower(t.dest_owner) and a.tbl_name=lower(t.dest_tablename) and a.col_idx<1000
                 where btdh='Y' and t.bvalid=1 and t.sysid = i_sp_id
                 group by a.db_name,a.tbl_name,a.tbl_location,t.tid
                 order by a.tbl_name)
       loop
                 o_return := o_return || c1.hsql || chr(10) ;
       end loop ;

   --���������TDH������������
   elsif i_kind = 'tdh_etl' then
       for c1 in(select '/mnt/dfs/xds/xds'||lower(sysid||'/'||dest_tablename)||'/logdate='||fn_imp_value('dest_part_value',tid)||'/*,'||
                        '/inceptor1/user/hive/warehouse/ods.db/add/'||lower(replace(dest,'.','_'))||'/etl_date='||fn_imp_value('dest_part_value',tid) hsql
                   from vw_imp_etl t
                  where btdh='Y' and t.bvalid=1 and t.sysid = i_sp_id)
       loop
                 o_return := o_return || c1.hsql || chr(10) ;
       end loop ;

   end if ;

   return o_return;
end;