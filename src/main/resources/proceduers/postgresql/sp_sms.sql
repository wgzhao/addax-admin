-- PostgreSQL version of sp_sms
-- 发送短信/告警入库
-- i_msg: 短消息内容
-- i_mobile: 逗号分隔的手机号或分组ID，额外包含默认分组“1”
-- i_sendtype: 三位开关，依次代表 短信/钉钉/电话（1=开启，0=关闭）

CREATE OR REPLACE FUNCTION sp_sms(
  i_msg      text,
  i_mobile   text DEFAULT '1',
  i_sendtype text DEFAULT '010'
) RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
  v_mobile text;
  v_msg    text;
  v_bsms   text;
  v_bkk    text;
  v_bcall  text;
BEGIN
  -- 解析接收人：将分组ID映射为手机号；若传入的是手机号则直接保留；仅保留在 vw_mobile_group 出现过的手机号
  WITH t_in AS (
         SELECT trim(x) AS gp
           FROM unnest(string_to_array(COALESCE(i_mobile,'') || ',1', ',')) AS x
       ),
       sel AS (
         SELECT DISTINCT COALESCE(a.mobile, t.gp) AS mobile
           FROM t_in t
           LEFT JOIN vw_mobile_group a
             ON a.groupid = t.gp
       )
  SELECT string_agg(mobile, ',')
    INTO v_mobile
    FROM sel
   WHERE mobile IN (SELECT mobile FROM vw_mobile_group);

  -- 生成消息内容（去掉尾部分号，追加时间戳），长度限制约 450 字符
  v_msg := substring(
            regexp_replace(gettd()::text || ':' || COALESCE(i_msg,'') , ';$', '', 'g')
            || ';' || E'\n' || to_char(current_timestamp,'YYYY-MM-DD HH24:MI:SS') || E'\n'
           FROM 1 FOR 450);

  -- 控制位：短信/钉钉/电话
  v_bsms := CASE
              WHEN (SELECT count(1) FROM tb_msg WHERE bsms<>'N' AND dw_clt_date >= current_timestamp - interval '1 minute') >= 30
                   THEN 'N'
              ELSE CASE substring(COALESCE(i_sendtype,'010') FROM 1 FOR 1) WHEN '1' THEN 'Y' ELSE 'N' END
            END;
  v_bkk  := CASE substring(COALESCE(i_sendtype,'010') FROM 2 FOR 1) WHEN '1' THEN 'Y' ELSE 'N' END;
  v_bcall:= CASE
              WHEN to_char(current_timestamp + interval '1 hour','HH24MI') BETWEEN '0000' AND '0800' THEN 'N'
              ELSE CASE substring(COALESCE(i_sendtype,'010') FROM 3 FOR 1) WHEN '1' THEN 'Y' ELSE 'N' END
            END;

  -- 写入消息表
  INSERT INTO tb_msg(phone, msg, bsms, bkk, bcall)
  SELECT v_mobile, v_msg, v_bsms, v_bkk, v_bcall;

  -- 不在函数中显式提交，交由调用方事务控制
END;
$$;
