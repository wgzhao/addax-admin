CREATE OR REPLACE procedure STG01.sp_imp_flag(i_kind in varchar2,i_group in varchar2,i_fid in varchar2,i_fval in int default 0)
as
   v_tradedate number(10,0);
begin
   --专门处理标志的过程
   v_tradedate := gettd() ;

   --新增标志
   if i_kind = 'add' then
      insert into stg01.tb_imp_flag(tradedate,kind,fid,fval)
      values(v_tradedate,i_group,i_fid,i_fval) ;

   --删除标志
   elsif i_kind = 'del' then
      delete from tb_imp_flag
       where tradedate = v_tradedate
         and instr(','||i_group||',' , ','||kind||',') > 0
         and fid = i_fid ;

   end if ;
   commit ;

exception
   when others then
        stg01.sp_sms('sp_imp_flag执行报错,kind=['||i_kind||'],group=['||i_group||'],fid=['||i_fid||'],fval='||i_fval||'],错误说明=['||sqlerrm||']','18692206867','110') ;
end;