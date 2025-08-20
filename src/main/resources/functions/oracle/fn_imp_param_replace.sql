CREATE OR REPLACE function fn_imp_param_replace(i_com_text in clob,i_param_sou in varchar2 := 'C')
  return clob as
  o_return clob;
begin
  o_return := i_com_text;

  --根据参数文件替换变量
  for c1 in (select a.param_kind, a.param_value
               from vw_imp_param a
              where a.param_sou = i_param_sou
                and a.param_kind is not null)
  loop
    o_return := replace(o_return, c1.param_kind, c1.param_value);
  end loop;

  --替换通用的特殊参数值
  o_return := replace(replace(replace(o_return,'${NOW}',to_char(sysdate, 'YYYY-MM-DD HH24:MI:SS')),'${NO}',to_char(sysdate, 'YYYYMMDD')),'${UUID}',to_char(rawtohex(sys_guid())));

  return o_return;
end;