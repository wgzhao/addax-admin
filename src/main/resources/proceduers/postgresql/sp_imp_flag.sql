-- PostgreSQL version of sp_imp_flag
CREATE OR REPLACE FUNCTION sp_imp_flag(i_kind varchar, i_group varchar, i_fid varchar, i_fval integer DEFAULT 0)
RETURNS void AS $$
DECLARE
   v_tradedate integer;
BEGIN
   -- 专门处理标志的过程
   v_tradedate := gettd();

   -- 新增标志
   IF i_kind = 'add' THEN
      INSERT INTO tb_imp_flag(tradedate, kind, fid, fval)
      VALUES (v_tradedate, i_group, i_fid, i_fval);

   -- 删除标志
   ELSIF i_kind = 'del' THEN
      DELETE FROM tb_imp_flag
       WHERE tradedate = v_tradedate
         AND position(',' || kind || ',' IN ',' || i_group || ',') > 0
         AND fid = i_fid;

   END IF;

EXCEPTION
   WHEN OTHERS THEN
      PERFORM sp_sms('sp_imp_flag执行报错,kind=[' || i_kind || '],group=[' || i_group ||
                     '],fid=[' || i_fid || '],fval=[' || i_fval || '],错误说明=[' || SQLERRM || ']',
                     '18692206867', '110');
      RAISE;
END;
$$ LANGUAGE plpgsql;
