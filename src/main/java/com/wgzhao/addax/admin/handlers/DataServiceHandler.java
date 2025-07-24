package com.wgzhao.addax.admin.handlers;

import com.wgzhao.addax.admin.utils.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 数据服务处理类
 * 负责处理数据服务相关的功能
 */
public class DataServiceHandler
{
    private static final String curpath = RedisUtils.get("path.bin");
    private static final String c_sql = RedisUtils.get("com.inicmd");
    private static final String c_sql3 = RedisUtils.get("com.iniout");

    /**
     * 数据服务的具体执行
     */
    public static boolean dsEtl(String dsId)
    {
        if (dsId == null || dsId.isEmpty() || !"1".equals(RedisUtils.flagAdd("ds." + dsId))) {
            return false;
        }

        String dest_dir = RedisUtils.get("path.oths");

        // 初始化redis
        System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":初始化redis配置信息...</b>" + RedisUtils.set("ds." + dsId, "0"));
        CommandExecutor.execute(c_sql3 + " \"select init_rds from vw_imp_ds2 where ds_id='" + dsId + "'\" |tee " + dest_dir + "/" + dsId + ".initrds");

        int result = CommandExecutor.executeWithResult("bash " + dest_dir + "/" + dsId + ".initrds 2>&1");
        if (result != 0) {
            RedisUtils.set("ds." + dsId, "1");
        }

        // 刷新ds视图
        if ("0".equals(RedisUtils.get("ds." + dsId)) && "1".equals(RedisUtils.get("ds." + dsId + ".dsview"))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":自定义查询转为presto视图...</b><p style='background-color:#A9A9A9'>");

            for (String kind : new String[] {"presto", "allsql"}) {
                System.out.println("利用" + kind + "刷新视图...");
                CommandExecutor.execute(c_sql3 + " \"select fn_imp_value('ds_sql_" + kind + "','" + dsId + "') from dual\" |tee " + dest_dir + "/" + dsId + ".dsview");

                if (CommandExecutor.executeWithResult("test $(wc -c " + dest_dir + "/" + dsId + ".dsview |awk '{print $1}') -gt 3") == 0) {
                    String cmd = kind.equals("presto") ? RedisUtils.get("com.prestocmd") : RedisUtils.get("com.prestoall");
                    result = CommandExecutor.executeWithResult(cmd + " -f " + dest_dir + "/" + dsId + ".dsview 2>&1");

                    if (result != 0) {
                        System.out.println("利用" + kind + "刷新视图失败!!!设置redis：" + RedisUtils.set("ds." + dsId, "1"));
                    }
                }
            }

            System.out.println("</p>");
        }

        // 获取目标表字段及ds下视图字段,更新涉及表
        if ("0".equals(RedisUtils.get("ds." + dsId)) && "1".equals(RedisUtils.get("ds." + dsId + ".bupdate"))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":获取目标表字段及ds下视图字段...</b><p style='background-color:#A9A9A9'>");

            String dbConns = CommandExecutor.executeForOutput(c_sql + " \"select sou_db_conn from vw_imp_ds2_mid where ds_id='" + dsId +
                    "' group by sou_db_conn union all select '" + dsId + "' from dual union all select 'hadoop' from dual\"");

            ExecutorService executor = Executors.newCachedThreadPool();

            for (String dbConn : dbConns.split("\n")) {
                if (dbConn == null || dbConn.trim().isEmpty()) {
                    continue;
                }

                executor.submit(() -> {
                    boolean success = EtlHandler.soutabEtl(dbConn);
                    if (!success) {
                        RedisUtils.set("ds." + dsId, "1");
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 更新数据服务涉及源表，用于计算是否完整配置了前置条件
            CommandExecutor.execute(c_sql + " \"begin sp_imp_alone('bupdate','D','" + dsId + "');end;\" 2>&1");

            // 更新完后，修改状态
            if ("0".equals(RedisUtils.get("ds." + dsId))) {
                System.out.println("</p>表结构获取完成，置更新状态为N");
                CommandExecutor.execute(c_sql + " \"update tb_imp_ds2 set bupdate='N' where ds_id='" + dsId + "'\" 2>&1");
            }
        }

        // 前置SQL
        if ("0".equals(RedisUtils.get("ds." + dsId)) && "1".equals(RedisUtils.get("ds." + dsId + ".pre_sql"))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":执行前置语句pre_sql...</b>");
            CommandExecutor.execute(c_sql3 + " \"select pre_sql from vw_imp_ds2 where ds_id='" + dsId + "'\" |tee " + dest_dir + "/" + dsId + ".presql");

            if (!SpAloneUtils.runSql(dest_dir + "/" + dsId + ".presql", 1)) {
                RedisUtils.set("ds." + dsId, "1");
            }
        }

        // 前置SH
        if ("0".equals(RedisUtils.get("ds." + dsId)) && "1".equals(RedisUtils.get("ds." + dsId + ".pre_sh"))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":执行前置脚本pre_sh...</b>");
            CommandExecutor.execute(c_sql3 + " \"select pre_sh from vw_imp_ds2 where ds_id='" + dsId + "'\" |tee " + dest_dir + "/" + dsId + ".presh");

            if (CommandExecutor.executeWithResult("bash " + dest_dir + "/" + dsId + ".presh") != 0) {
                RedisUtils.set("ds." + dsId, "1");
            }
        }

        // 开始数据推送
        if ("0".equals(RedisUtils.get("ds." + dsId))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":开始数据服务推送...</b>");

            CommandExecutor.execute(c_sql3 + " \"select tbl_id from tb_imp_ds2_tbls where ds_id='" + dsId +
                    "' and flag='N' order by end_time-start_time desc\" |tee " + dest_dir + "/" + dsId + ".txt");

            String tblIds = LogUtils.readFile(dest_dir + "/" + dsId + ".txt");
            ExecutorService executor = Executors.newCachedThreadPool();

            for (String tblId : tblIds.split("\n")) {
                if (tblId.trim().isEmpty()) {
                    continue;
                }

                String rc = SpAloneUtils.waitIdxResult("ds_" + dsId, Integer.parseInt(RedisUtils.get("ds." + dsId + ".paral_num")));

                executor.submit(() -> {
                    System.out.println("======" + DateUtils.getCurrentDateTime() + ":[" + tblId + "][并发队列号:" + rc + "]开始执行======");
                    CommandExecutor.execute(c_sql + " \"begin sp_imp_status('cR','" + tblId + "');end;\"");

                    // 获取服务JSON及具体执行命令（文件及关系型数据库，通过cmd区分）
                    CommandExecutor.execute(c_sql3 + " \"select fn_imp_value('ds_json','" + tblId + "') from dual\" >" + dest_dir + "/" + tblId + ".json");
                    CommandExecutor.execute(c_sql3 + " \"select fn_imp_value('ds_cmd','" + tblId + "') from dual\" >" + dest_dir + "/" + tblId + ".sh");

                    // 命令具体执行
                    int retCode;
                    if ("1".equals(RedisUtils.get("ds." + dsId + ".paral_num"))) {
                        System.out.println("<p style='background-color:#A9A9A9'>");
                        retCode = CommandExecutor.executeWithResult("bash " + dest_dir + "/" + tblId + ".sh " + dest_dir + "/" + tblId + ".json 2>&1");
                    }
                    else {
                        retCode = CommandExecutor.executeWithResult("bash " + dest_dir + "/" + tblId + ".sh " + dest_dir + "/" + tblId + ".json 2>/dev/null 1>/dev/null");
                    }

                    if (retCode == 0) {
                        CommandExecutor.execute(c_sql + " \"begin sp_imp_status('cY','" + tblId + "');end;\"");
                    }
                    else {
                        RedisUtils.set("ds." + dsId, "1");
                        CommandExecutor.execute(c_sql + " \"begin sp_imp_status('cE','" + tblId + "');end;\"");
                        System.out.println(tblId + "执行失败，等待10秒后继续");
                        try {
                            Thread.sleep(10000);
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    System.out.println("</p>======" + DateUtils.getCurrentDateTime() + ":[" + tblId + "][并发队列号:" + rc + "]执行结束======" +
                            RedisUtils.flagRemove("ds_" + dsId + "_" + rc));
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 后置SQL
        if ("0".equals(RedisUtils.get("ds." + dsId)) && "1".equals(RedisUtils.get("ds." + dsId + ".post_sql"))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":执行后置语句post_sql...</b>");
            CommandExecutor.execute(c_sql3 + " \"select post_sql from vw_imp_ds2 where ds_id='" + dsId + "'\" |tee " + dest_dir + "/" + dsId + ".postsql");

            if (!SpAloneUtils.runSql(dest_dir + "/" + dsId + ".postsql", 1)) {
                RedisUtils.set("ds." + dsId, "1");
            }
        }

        // 后置SH
        if ("0".equals(RedisUtils.get("ds." + dsId)) && "1".equals(RedisUtils.get("ds." + dsId + ".post_sh"))) {
            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":执行后置脚本post_sh...</b>");
            CommandExecutor.execute(c_sql3 + " \"select post_sh from vw_imp_ds2 where ds_id='" + dsId + "'\" |tee " + dest_dir + "/" + dsId + ".postsh");

            if (CommandExecutor.executeWithResult("bash " + dest_dir + "/" + dsId + ".postsh") != 0) {
                RedisUtils.set("ds." + dsId, "1");
            }
        }

        // 执行完毕
        System.out.println("数据服务执行结果：" + RedisUtils.get("ds." + dsId));
        if ("0".equals(RedisUtils.get("ds." + dsId))) {
            CommandExecutor.execute(c_sql + " \"begin sp_imp_status('Y','" + dsId + "');end;\"");
        }
        else {
            CommandExecutor.execute(c_sql + " \"begin sp_imp_status('E','" + dsId + "');end;\"");
        }

        // 清理redis
        System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":清理redis配置信息...</b>");
        String redisKeys = CommandExecutor.executeForOutput("rds \"keys ds." + dsId + "*\"");
        for (String rs : redisKeys.split("\n")) {
            if (rs != null && !rs.trim().isEmpty()) {
                System.out.println("删除[" + rs + "]:" + CommandExecutor.executeForOutput("rds \"del " + rs + "\""));
            }
        }

        RedisUtils.flagRemove("ds." + dsId);
        return true;
    }
}