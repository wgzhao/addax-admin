CREATE OR REPLACE function fn_imp_pnname(i_pntype   in varchar2,
                                         i_fixed    in varchar2 default null,
                                         i_interval in int default null,
                                         i_range    in varchar2 default null)
  return varchar2 RESULT_CACHE as
  o_return  varchar2(255);
  v_strname varchar2(255);
begin
  --计划类型名称
  select entry_content
    into o_return
    from tb_dictionary
   where entry_code = '1064'
     and entry_value = i_pntype;
  --计划类型具体定义
  if i_pntype is not null and
     not (i_fixed is null and i_interval is null and i_range is null) then
    o_return := o_return || case
                  when i_fixed is not null then
                   '定时' || i_fixed
                  else
                   i_range || '内_间隔' || i_interval || '分钟'
                end;
  end if;

  return o_return;
end;