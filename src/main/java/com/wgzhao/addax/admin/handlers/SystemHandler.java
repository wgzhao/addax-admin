package com.wgzhao.addax.admin.handlers;


import com.wgzhao.addax.admin.utils.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 系统处理类
 * 负责处理系统相关的功能，如自动备份、系统检测等
 */
public class SystemHandler {
    private static final String rootdir = RedisUtils.get("path.infalog");
    private static final String curpath = RedisUtils.get("path.bin");
    private static final String c_sql = RedisUtils.get("com.inicmd");
    private static final String c_sql3 = RedisUtils.get("com.iniout");
    private static final String c_presto = RedisUtils.get("com.prestocmd");
    private static final String c_allsql = RedisUtils.get("com.prestoall");

    private static final String v_today = DateUtils.getCurrentDate("yyyyMMdd");
    private static final String v_yest = DateUtils.getYesterdayDate("yyyyMMdd");

    /**
     * 自动备份功能
     */
    public static boolean autoBak() {
        String bakdir = rootdir + "/autobak";

        // bin备份
        System.out.println("</p>\n" + DateUtils.getCurrentDateTime() + ":=====bin备份=====<p style='background-color:#A9A9A9'>");
        String bakfile = bakdir + "/autobak_bin_" + v_today + ".tar";
        CommandExecutor.execute("rm -f " + bakfile);
        CommandExecutor.execute("tar -cvf " + bakfile + " " + rootdir + "/bin");

        // 日志文件归档
        System.out.println("</p>\n\n" + DateUtils.getCurrentDateTime() + ":=====日志文件归档=====<p style='background-color:#A9A9A9'>");
        String logdir = RedisUtils.get("path.runlog");
        CommandExecutor.execute("mkdir -p " + logdir + "/" + v_yest);
        CommandExecutor.execute("find " + logdir + "/ -path \"" + logdir + "/" + v_yest + "\" -a -prune -o -name \"*_" + v_yest + "_*\" -type f -exec mv -f {} " + logdir + "/" + v_yest + "/ \\;");

        // 备份文件压缩存档
        System.out.println("</p>\n\n" + DateUtils.getCurrentDateTime() + ":=====备份文件压缩存档=====<p style='background-color:#A9A9A9'>");
        bakfile = bakdir + "/autobak_" + v_today + ".tar.gz";
        CommandExecutor.execute("rm -f " + bakfile);
        CommandExecutor.execute("tar -zcvf " + bakfile + " " + bakdir + "/autobak_*_" + v_today + ".*");
        CommandExecutor.execute("rm -f " + bakdir + "/autobak_*_" + v_today + ".*");
        CommandExecutor.execute("cp " + bakfile + " /mnt/dfs/user/hive/autobak/");

        // 删除历史数据
        System.out.println("</p>\n\n" + DateUtils.getCurrentDateTime() + ":=====删除历史数据=====<p style='background-color:#A9A9A9'>");
        CommandExecutor.execute("find " + bakdir + "/ -type f -ctime +12 -exec rm {} \\;");

        // 其他处理事项
        System.out.println("</p>\n\n" + DateUtils.getCurrentDateTime() + ":=====其他处理事项=====<p style='background-color:#A9A9A9'>");
        CommandExecutor.execute("ln " + rootdir + "/nohup.out " + rootdir + "/log/tuna_nohup_" + v_today + "_0000.log");

        System.out.println("</p>");
        return true;
    }

    /**
     * 切日工作流，整体替换WF_PARAM_FILE
     */
    public static boolean updtParam() {
        String currentTime = DateUtils.getCurrentDateTime("HHmm");
        if (!"1630".equals(currentTime)) {
            SpAloneUtils.syslog("参数更新任务不能执行,任务退出,非切日时间点");
            return false;
        }

        CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('系统参数param_sys开始切换'||chr(10)||to_char(stg01.fn_imp_value('pntype_list'))||chr(10)||'TD=" +
                RedisUtils.get("param.TD") + "'||chr(10)||'CD=" + RedisUtils.get("param.CD") + "','1','110');end;\"");

        // 计算日期参数
        for (String td : new String[]{"sysdate", "sysdate-1", "sysdate+1"}) {
            int result = CommandExecutor.executeWithResult(c_sql + " \"begin stg01.sp_imp_param(to_char(" + td + ",'YYYYMMDD'));end;\"");
            if (result != 0) {
                CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('系统参数param_sys生成失败!!!!','1','111');end;\"");
                return false;
            }
        }

        // 更新薪酬参数
        CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_alone('xc_init');end;\"");

        // 读取配置信息更新redis，包含日期参数、常用地址，命令中文等
        String rdsCommands = CommandExecutor.executeForOutput(c_sql + " \"select rds from stg01.vw_updt_rds\"");
        for (String line : rdsCommands.split("\n")) {
            if (line != null && !line.trim().isEmpty()) {
                System.out.println("更新redis：" + line + "===>" + CommandExecutor.executeForOutput("rds \"" + line + "\""));
            }
        }

        CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('系统参数param_sys切换完成！'||chr(10)||to_char(stg01.fn_imp_value('pntype_list'))||chr(10)||'TD=" +
                RedisUtils.get("param.TD") + "'||chr(10)||'CD=" + RedisUtils.get("param.CD") + "','1','110');end;\"");

        // 记录数比对表增加分区
        CommandExecutor.execute("hive -e \"alter table default.tab_cnt add if not exists partition(logdate='" + RedisUtils.get("param.TD") + "')\"");

        SpAloneUtils.syslog("参数更新任务执行完毕");
        return true;
    }

    /**
     * 检测ds调度工具是否正常
     */
    public static boolean dschk() {
        SpAloneUtils.syslog("检测ds，" + DateUtils.getCurrentDateTime("yyyy-MM-dd_HH:mm:ss") + ":由" + CommandExecutor.getHostname() + "发起，开始");

        // 如果是整点前2分钟内，添加消息标志
        int currentMinute = Integer.parseInt(DateUtils.getCurrentDateTime("mm"));
        if (currentMinute <= 1) {
            RedisUtils.flagAdd("dschk.msg");
        }

        System.out.println(DateUtils.getCurrentDateTime() + ":利用调度工具设置标志");
        EtlHandler.startWkf("manual", "export PATH=/home/hive/bin:$PATH;rds 'set dschk 1'");

        System.out.println("\n\n" + DateUtils.getCurrentDateTime() + ":等待工作流执行完毕...");
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n" + DateUtils.getCurrentDateTime() + ":获取标志");
        if ("1".equals(RedisUtils.get("dschk"))) {
            System.out.println("调度工具运行平稳..." + RedisUtils.set("dschk", "0"));
            RedisUtils.set("dschk.errcnt", "0"); // 错误数归0

            if ("1".equals(RedisUtils.flagHas("dschk.msg"))) {
                CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('" + DateUtils.getCurrentDateTime() +
                        ":调度工具运行平稳~~来自于" + CommandExecutor.getHostname() + "','1','010');end;\"");
            }
        } else {
            System.out.println("调度工具异常，需要赶紧处理");
            int errCount = 0;
            try {
                errCount = Integer.parseInt(RedisUtils.get("dschk.errcnt"));
            } catch (NumberFormatException e) {
                // 忽略解析错误
            }

            errCount++;
            RedisUtils.set("dschk.errcnt", String.valueOf(errCount)); // 错误数+1

            CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('" + DateUtils.getCurrentDateTime() +
                    ":调度工具连续异常" + errCount + "次，需要赶紧处理!!来自于" +
                    CommandExecutor.getHostname() + "','dschk','110');end;\"");
        }

        RedisUtils.flagRemove("dschk.msg");
        SpAloneUtils.syslog("检测ds，" + DateUtils.getCurrentDateTime("yyyy-MM-dd_HH:mm:ss") + ":由" + CommandExecutor.getHostname() + "发起，结束");
        return true;
    }

    /**
     * 系统检测
     */
    public static boolean syschk() {
        if (!"1".equals(RedisUtils.flagAdd("syschk"))) {
            return false;
        }

        // 更新检测的配置信息
        CommandExecutor.execute(c_sql + " \"truncate table stg01.tb_imp_chk_inf\"");
        CommandExecutor.execute(c_sql + " \"insert into stg01.tb_imp_chk_inf(engine,chk_idx,chk_sendtype,chk_mobile,bpntype,chk_kind,chk_sql) " +
                "select engine,chk_idx,chk_sendtype,chk_mobile,bpntype,chk_kind,chk_sql from stg01.vw_imp_chk_inf\"");

        CommandExecutor.execute(c_sql + " \"delete from stg01.tb_imp_chk where chk_kind not in(select chk_kind from stg01.tb_imp_chk_inf)\"");

        // 网络连通性检测部分已注释，如需实现可以取消注释
        /*
        System.out.println("<b>" + DateUtils.getCurrentDateTime() + ":获取网络连通性</b><p style='background-color:#A9A9A9'>");
        String inifile = RedisUtils.get("path.trans") + "/netchk.txt";
        CommandExecutor.execute(c_sql3 + " \"select distinct sysid||'_'||sys_name||','||netchk from stg01.vw_imp_system where netchk is not null order by 1\" >" + inifile);

        ExecutorService executor = Executors.newCachedThreadPool();

        for (String line : LogUtils.readLines(inifile)) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            executor.submit(() -> {
                String[] parts = line.split(",");
                if (parts.length < 2) {
                    return;
                }

                String result = CommandExecutor.executeForOutput("/opt/anaconda3/bin/python3 " + curpath + "/package/netchk.py " + parts[1]);
                System.out.println(DateUtils.getCurrentDateTime() + ":" + String.join(" ", parts) + "==>" + result);

                if (result.contains("Closed")) {
                    CommandExecutor.execute(c_sql + " \"insert into stg01.tb_imp_chk(chk_kind,chk_name,chk_content) values('netchk','" +
                                          parts[0] + "','" + parts[0] + "[" + parts[1] + "]连通性异常')\"");
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        */

        CommandExecutor.execute(c_sql + " \"delete from stg01.tb_imp_chk where chk_kind in(select chk_kind from stg01.tb_imp_chk_inf where engine='allsql')\"");

        // 基于allsql的自定义检测
        System.out.println("</p><b>" + DateUtils.getCurrentDateTime() + ":基于allsql的自定义检测</b><p style='background-color:#A9A9A9'>");

        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int grp = 1; grp <= 5; grp++) {
            final int groupNum = grp;
            executor.submit(() -> {
                String sqlfile = RedisUtils.get("path.oths") + "/syschk_" + groupNum + ".sql";
                CommandExecutor.execute(RedisUtils.get("com.iniout") + " \"select chk from (select 'insert into ora_in.stg01.tb_imp_chk(chk_mobile,chk_sendtype,chk_kind,chk_name,chk_content)'||" +
                        "chr(10)||stg01.fn_imp_param_replace(chk_sql)||';' as chk,mod(row_number()over(order by chk_idx),5)+1 px from stg01.tb_imp_chk_inf where engine='allsql') where px=" +
                        groupNum + "\" |tee " + sqlfile);

                int result = CommandExecutor.executeWithResult(RedisUtils.get("com.prestoall") + " -f " + sqlfile + " 2>&1");
                if (result != 0) {
                    CommandExecutor.execute(RedisUtils.get("com.inicmd") + " \"begin stg01.sp_sms('" + groupNum + "组的系统检测失败','1','110');end;\"");
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 系统整体异常检测
        System.out.println("</p><b>" + DateUtils.getCurrentDateTime() + ":系统整体异常检测</b><p style='background-color:#A9A9A9'>");
        int result = CommandExecutor.executeWithResult(c_sql + " \"begin stg01.sp_imp_alone('syschk');end;\" 2>&1");
        if (result != 0) {
            CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('系统检测函数报错,请及时处理!!','1','110');end;\"");
        }

        RedisUtils.flagRemove("syschk");
        System.out.println("</p>");
        return true;
    }
}
