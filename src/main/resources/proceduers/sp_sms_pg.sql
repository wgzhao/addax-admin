CREATE OR REPLACE FUNCTION sp_sms(i_msg text, i_mobile varchar DEFAULT '1', i_sendtype varchar DEFAULT '010')
RETURNS void AS $$
       -- 发送短信存储过程
       -- i_msg:短信内容，多条内容用;分隔
       -- i_mobile:收件人手机号，支持组编号和手机号
       -- i_sendtype:短信发送类型，三位数字，KK通话需求1为发送，0为不发送
       -- 23点到7点之间不发送通话类短信
       -- 发短信频率限制：如果一小时内发送短信超过30条，则停止发送
DECLARE
       v_mobile text;
       c1 RECORD;
BEGIN
       -- 根据逗号分隔的i_mobile参数处理收件人手机号
       WITH t_in AS (
        SELECT unnest(string_to_array(i_mobile || ',1', ',')) AS gp
       )
       SELECT string_agg(DISTINCT COALESCE(a.mobile, t.gp), ',')
              INTO v_mobile
         FROM t_in t
         LEFT JOIN vw_mobile_group a
           ON a.groupid = t.gp
        WHERE COALESCE(a.mobile, t.gp) IN (SELECT mobile FROM vw_mobile_group);

       INSERT INTO tb_msg(phone, msg, bsms, bkk, bcall)
       SELECT v_mobile,
              substring(regexp_replace(gettd()::text || ':' || i_msg, ';$', '') || ';' || E'\n' || to_char(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS') || E'\n' FROM 1 FOR 450),
              -- 1小时内发送短信超过30条,则停止发送短信
              CASE WHEN (SELECT count(1) FROM tb_msg WHERE bsms <> 'N' AND dw_clt_date >= CURRENT_TIMESTAMP - interval '1 hour') >= 30 
                   THEN 'N' 
                   ELSE CASE WHEN substring(i_sendtype FROM 1 FOR 1) = '1' THEN 'Y' ELSE 'N' END 
              END,
              CASE WHEN substring(i_sendtype FROM 2 FOR 1) = '1' THEN 'Y' ELSE 'N' END,
              CASE WHEN to_char(CURRENT_TIMESTAMP + interval '1 hour', 'HH24MI') BETWEEN '0000' AND '0800' 
                   THEN 'N' 
                   ELSE CASE WHEN substring(i_sendtype FROM 3 FOR 1) = '1' THEN 'Y' ELSE 'N' END 
              END;

END;
$$ LANGUAGE plpgsql;