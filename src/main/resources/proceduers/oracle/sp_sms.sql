CREATE OR REPLACE procedure sp_sms(i_msg in varchar2,i_mobile in varchar2:='1',i_sendtype in varchar2:='010')
       --���������������
       --i_msg:���������������������
       --i_mobile:���������������������������������
       --i_sendtype:���������������������������������������KK���������������1������������0���������
       --23������7������������������������
       --���������������������������������������������������������������
as
       v_mobile varchar2(255) ;
begin
       --������������i_mobile������������������������
       with t_in as
        (select regexp_substr(c1, '[^,]+', 1, level) gp
           from (select i_mobile||',1' c1 from dual)
         connect by level <= regexp_count(c1, ',+') + 1)
       select to_char(wm_concat(mobile))
              into v_mobile
         from (select distinct nvl(a.mobile, t.gp) mobile
                 from t_in t
                 left join vw_mobile_group a
                   on a.groupid = t.gp)
        where mobile in(select mobile from vw_mobile_group) ;

       insert into tb_msg(phone,msg,bsms,bkk,bcall)
       select v_mobile,
              substrb(regexp_replace(gettd()||':'||i_msg,';$','')||';'||chr(10)||to_char(sysdate,'YYYY-MM-DD HH24:MI:SS')||chr(10),1,450),
              --1���������������������������30���������,������������������
              case when (select count(1) from tb_msg where bsms<>'N' and dw_clt_date>=sysdate-1/24/60)>=30 then 'N' else decode(substr(i_sendtype,1,1),'1','Y','N') end,
              decode(substr(i_sendtype,2,1),'1','Y','N'),
              case when to_char(sysdate+1/24,'HH24MI') between '0000' and '0800' then 'N' else decode(substr(i_sendtype,3,1),'1','Y','N') end
       from dual ;

       commit ;
end;