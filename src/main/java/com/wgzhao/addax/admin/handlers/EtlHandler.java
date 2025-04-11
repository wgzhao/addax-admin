package com.wgzhao.addax.admin.handlers;

import com.wgzhao.addax.admin.utils.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Arrays;

/**
 * ETL相关处理类
 * 负责处理数据抽取、转换、加载相关的功能
 */
public class EtlHandler
{
    private static final String curpath = RedisUtils.get("path.bin");
    private static final String c_sql = RedisUtils.get("com.inicmd");
    private static final String c_sql3 = RedisUtils.get("com.iniout");

    /**
     * 采集,SP,计划任务的具体执行
     * 参数1：任务ID
     * 参数2：默认不传，代表执行采集或者SP；如果传入plan，代表计划任务；如果传入manual,表示前台单独执行
     */
    public static boolean spEtl(String taskId, String mode)
    {
        if (taskId == null || taskId.isEmpty()) {
            return false;
        }

        if ("1".equals(RedisUtils.flagAdd("sp." + taskId))) {
            String spname = CommandExecutor.executeForOutput(c_sql3 + " \"select nvl(stg01.fn_imp_value('taskname','" + taskId + "'),'" + taskId + "') from dual\"");
            System.out.println("<b>" + DateUtils.getCurrentDateTime() + ":" + taskId + "[" + spname + "]开始执行...</b>");

            if ("manual".equals(mode)) {
                CommandExecutor.execute(c_sql + " \"update tb_imp_sp_com set flag='N' where sp_id='" + taskId + "' and flag!='X'\"");
            }

            String comtList = CommandExecutor.executeForOutput(c_sql3 + " \"select com_id||','||com_kind||','||com_idx from stg01.tb_imp_sp_com where sp_id='" + taskId + "' and flag='N' order by com_idx\"");
            for (String comt : comtList.split("\n")) {
                if (comt.trim().isEmpty()) {
                    continue;
                }

                String[] parts = comt.split(",");
                if (parts.length < 3) {
                    continue;
                }

                String com_id = parts[0];
                String com_kind = parts[1];
                String com_idx = parts[2];

                if (com_id.isEmpty() || com_kind.isEmpty() || com_idx.isEmpty()) {
                    continue;
                }

                // 获取脚本内容
                String com_file = RedisUtils.get("path.coms") + "/" + spname + "_" + com_idx + ".txt";
                System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":生成文件" + com_file + "...</b>");

                int result = CommandExecutor.executeWithResult(c_sql3 + " \"select stg01.fn_imp_value('com_text','" + com_id + "') from dual\" >" + com_file);
                if (result == 0) {
                    System.out.println("生成成功，置命令状态为R");
                    CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_status('cR','" + com_id + "');end;\" 2>&1");
                }
                else {
                    System.out.println("生成失败，跳过");
                    CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_status('cE','" + com_id + "');end;\" 2>&1");

                    if ("plan".equals(mode)) {
                        // 计划需要继续执行,只是跳过报错的一条计划
                        continue;
                    }
                    else {
                        // 采集或者SP计算，命令报错，中止主任务；计划任务不能终止
                        break;
                    }
                }

                // 开始执行脚本
                System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":执行文件" + com_file + "...</b><p style='background-color:#A9A9A9'>");
                result = CommandExecutor.executeWithResult(curpath + "/tuna.py -t 36000 -m " + com_kind + " -f " + com_file + " 2>&1");

                if (result == 0) {
                    CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_status('cY','" + com_id + "');end;\" 2>&1");
                }
                else {
                    CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_status('cE','" + com_id + "');end;\" 2>&1");

                    if ("plan".equals(mode)) {
                        // 计划需要继续执行,只是跳过报错的一条计划
                        continue;
                    }
                    else {
                        // 采集或者SP计算，命令报错，中止主任务；计划任务不能终止
                        break;
                    }
                }
                System.out.println("</p>");
            }

            System.out.println("\n<b>" + DateUtils.getCurrentDateTime() + ":" + taskId + "[" + spname + "]执行结束...</b>" + RedisUtils.flagRemove("sp." + taskId));

            if (!"manual".equals(mode)) {
                CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_status('Y','" + taskId + "');end;\" 2>&1");
                // 计划任务执行无需重新调起sp_start
                if (!"plan".equals(mode)) {
                    return spStart();
                }
            }
            return true;
        }
        else {
            if (!"manual".equals(mode)) {
                CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_status('E','" + taskId + "');end;\" 2>&1");
            }
            return false;
        }
    }

    /**
     * 启动任务的并发入口
     */
    public static boolean spStart()
    {
        if ("1".equals(RedisUtils.flagAdd("auto"))) {
            System.out.println(DateUtils.getCurrentDateTime() + ":当前redis标志情况:\n" + CommandExecutor.executeForOutput("rfg all 1"));
            CommandExecutor.execute("rfg wtout sp_init");
            startWkf("sp_init", "");
            RedisUtils.flagRemove("auto");
            System.out.println("\n" + DateUtils.getCurrentDateTime() + ":当前redis标志情况:\n" + CommandExecutor.executeForOutput("rfg all 1"));
            return true;
        }
        return false;
    }

    /**
     * 采集,SP,数据服务的总入口
     */
    public static boolean spInit()
    {
        if ("1".equals(RedisUtils.flagAdd("sp_init"))) {
            String shfile = RedisUtils.get("path.oths") + "/sp_init.sh";
            CommandExecutor.execute(">" + shfile);
            String strsql = "";

            System.out.println("#####" + DateUtils.getCurrentDateTime() + ":数据源采集完毕######");
            String sysids = CommandExecutor.executeForOutput(c_sql3 + " \"select stg01.fn_imp_value('etl_end') from dual\"");

            for (String sysid : sysids.split("\n")) {
                if (sysid == null || sysid.trim().isEmpty()) {
                    continue;
                }

                // 置数据源采集结束
                strsql += "stg01.sp_imp_alone('etl_end','" + sysid + "');";
                // 数据源采集完后的操作
                CommandExecutor.execute("echo \"" + RedisUtils.get("com.sp_alone") + " start_wkf etl_end " + sysid + "\" |tee -a " + shfile);
            }

            if (!strsql.isEmpty()) {
                System.out.println(DateUtils.getCurrentDateTime() + ":执行SQL=[" + strsql + "]");
                CommandExecutor.execute(c_sql + " \"begin " + strsql + " end;\" 2>&1");
                CommandExecutor.execute("bash " + shfile + " 2>&1");
            }

            System.out.println("\n\n#####" + DateUtils.getCurrentDateTime() + ":需要执行的采集、SP计算、数据服务######");
            strsql = "";
            CommandExecutor.execute(">" + shfile);

            int result = CommandExecutor.executeWithResult(c_sql + " \"begin stg01.sp_imp_alone('sp_start');end;\" 2>&1");

            if (result == 0) {
                String lines = CommandExecutor.executeForOutput(c_sql3 + " \"select stg01.fn_imp_value('sp_run') from dual\"");

                for (String line : lines.split("\n")) {
                    if (line == null || line.trim().isEmpty()) {
                        continue;
                    }

                    // 置状态为R
                    String[] linev = line.split("\\|");
                    strsql += "stg01.sp_imp_status('R','" + linev[1] + "');";
                    CommandExecutor.execute("echo \"" + RedisUtils.get("com.sp_alone") + " start_wkf " + linev[0] + " " + linev[1] + "\" |tee -a " + shfile);
                }

                if (!strsql.isEmpty()) {
                    System.out.println(DateUtils.getCurrentDateTime() + ":执行SQL=[" + strsql + "]");
                    CommandExecutor.execute(c_sql + " \"begin " + strsql + " end;\"");
                    CommandExecutor.execute("bash " + shfile + " 2>&1");
                }
            }

            // 如果没有日志输出，不保留日志文件
            String logfile = RedisUtils.get("logfile");
            if (LogUtils.countLines(logfile) <= 8) {
                LogUtils.deleteFile(logfile);
            }

            RedisUtils.flagRemove("sp_init");
            return true;
        }
        return false;
    }

    /**
     * 获取表的字段信息
     */
    public static boolean soutabEtl(String dbConn)
    {
        if (dbConn == null || dbConn.isEmpty()) {
            return false;
        }

        if ("1".equals(RedisUtils.flagAdd("soutab." + dbConn))) {
            // 获取表结构
            String jsonfile = RedisUtils.get("path.oths") + "/soutab_" + dbConn + ".json";
            System.out.println("\n" + DateUtils.getCurrentDateTime() + ":获取表结构信息[" + dbConn + "][" + jsonfile + "]...");

            CommandExecutor.execute(c_sql3 + " \"select col_json from stg01.vw_imp_etl_soutab where sou_db_conn='" + dbConn + "'\" |tee " + jsonfile);

            if (LogUtils.countLines(jsonfile) >= 5) {
                int result = CommandExecutor.executeWithResult(RedisUtils.get("com.tuna") + " -m schema -f " + jsonfile + " 2>&1");

                if (result == 0) {
                    CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_alone('bupdate','" + dbConn + "','n');end;\"");
                    RedisUtils.flagRemove("soutab." + dbConn);
                    return true;
                }
                else {
                    CommandExecutor.execute(c_sql + " \"begin stg01.sp_sms('获取" + dbConn + "的表结构信息失败!!!!','1','010');end;\"");
                }
            }

            RedisUtils.flagRemove("soutab." + dbConn);
        }
        return false;
    }

    /**
     * 源表信息获取启动
     */
    public static boolean soutabStart()
    {
        if ("1".equals(RedisUtils.flagAdd("soutab"))) {
            // 获取源库及hadoop的表结构信息
            System.out.println("<b>" + DateUtils.getCurrentDateTime() + ":获取源库及hadoop的表结构信息</b><p style='background-color:#A9A9A9'>");

            String dest_dir = RedisUtils.get("path.oths");
            String dbConns = CommandExecutor.executeForOutput(c_sql + " \"select sou_db_conn from stg01.vw_imp_etl_soutab where kind='etl'\"");

            ExecutorService executor = Executors.newCachedThreadPool();

            for (String dbConn : dbConns.split("\n")) {
                if (dbConn == null || dbConn.trim().isEmpty()) {
                    continue;
                }

                executor.submit(() -> {
                    System.out.println(DateUtils.getCurrentDateTime() + ":" + dbConn + "...start");
                    soutabEtl(dbConn);
                    System.out.println(DateUtils.getCurrentDateTime() + ":" + dbConn + "...over");
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 刷新对比表
            CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_alone('colexch_updt');end;\" 2>&1");

            // 建表或者刷新hive表结构
            String dest_file = dest_dir + "/updt_hive.sql";

            for (String kind : Arrays.asList("updt_hive", "updt_mysql")) {
                CommandExecutor.execute(c_sql3 + " \"select stg01.fn_imp_value('" + kind + "') from dual\" >" + dest_file);

                if (CommandExecutor.executeWithResult("test -s " + dest_file) == 0) {
                    System.out.println("</p>\n<b>" + DateUtils.getCurrentDateTime() + ":" + kind + " " +
                            RedisUtils.flagAdd("soutab.task") + "</b>[" + dest_file + "]<p style='background-color:#A9A9A9'>");

                    if ("updt_hive".equals(kind)) {
                        CommandExecutor.execute(curpath + "/tuna.py -m hive -f " + dest_file + " 2>&1");
                    }
                    else {
                        CommandExecutor.execute("cat " + dest_file);
                        CommandExecutor.execute("HOME=/opt/infalog mysql -hnn01 -P3306 -Dhive -uhive -p'ZEQEJGsNP7NT' <" + dest_file);
                    }
                }
            }

            // 获取更新后的hadoop表结构信息，并刷新对比表
            if ("1".equals(RedisUtils.flagHas("soutab.task"))) {
                System.out.println("</p>\n<b>" + DateUtils.getCurrentDateTime() + ":本次hadoop有更新，获取更新后的hadoop表结构信息</b><p style='background-color:#A9A9A9'>");
                soutabEtl("hadoop");
                CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_alone('colexch_updt');end;\" 2>&1");
            }

            RedisUtils.flagRemove("soutab.task");

            // 更新状态及采集JSON
            System.out.println("</p>\n<b>" + DateUtils.getCurrentDateTime() + ":执行完毕，更新状态及采集JSON</b>");
            CommandExecutor.execute(c_sql + " \"begin stg01.sp_imp_alone('bupdate','N');end;\" 2>&1");

            RedisUtils.flagRemove("soutab");
            return true;
        }
        return false;
    }

    /**
     * ODS采集完后，需要执行的后续操作
     */
    public static boolean etlEnd(String sysid, String param)
    {
        if (sysid == null || sysid.isEmpty()) {
            return false;
        }

        // 1、更新redis中api参数,用于接口的日期参数，该参数是以数据源编号开头的，用于区分不同数据源的采集情况
        String rdsCommands = CommandExecutor.executeForOutput(c_sql + " \"select replace(rds,'set param.','set api." + sysid +
                "') rds from stg01.vw_updt_rds where rds like 'set param.%'\"");

        for (String line : rdsCommands.split("\n")) {
            if (line != null && !line.trim().isEmpty()) {
                System.out.println("更新redis:" + line + "===>" + CommandExecutor.executeForOutput("rds \"" + line + "\""));
            }
        }

        // 2、ODS采集表记录数及比对
        if (param == null || param.isEmpty() || "tblcnt".equals(param)) {
            String tblcnt = RedisUtils.get("path.oths") + "/tblcnt_" + sysid + ".txt";
            String tblsql = RedisUtils.get("path.oths") + "/tblcnt_" + sysid + ".sql";

            CommandExecutor.execute(">" + tblcnt);
            CommandExecutor.execute(c_sql3 + " \"select stg01.fn_imp_value('etl_end_chk','" + sysid + "') from dual\" >" + tblsql);
            CommandExecutor.execute(RedisUtils.get("com.prestocmd") + " --output-format TSV -f " + tblsql + " |tee -a " + tblcnt);
            CommandExecutor.execute("cp " + tblcnt + " /mnt/dfs/sta/stage/tab_cnt/logdate=" + RedisUtils.get("param.TD") + "/");
        }

        return true;
    }

    /**
     * 统一调起工作流
     */
    public static boolean startWkf(String type, String param)
    {
        String comt;

        switch (type) {
            case "plan":
                comt = RedisUtils.get("com.sp_alone") + " sp_etl " + param + " plan";
                break;
            case "judge":
                comt = RedisUtils.get("com.sp_alone") + " judge_etl " + param;
                break;
            case "ds":
                comt = RedisUtils.get("com.sp_alone") + " ds_etl " + param;
                break;
            case "soutab":
                comt = RedisUtils.get("com.sp_alone") + " soutab_etl " + param;
                break;
            case "sp":
                comt = RedisUtils.get("com.sp_alone") + " sp_etl " + param;
                break;
            case "spcom":
                comt = RedisUtils.get("com.sp_alone") + " sp_etl " + param + " manual";
                break;
            case "manual":
                comt = param;
                break;
            default:
                comt = RedisUtils.get("com.sp_alone") + " " + type + " " + param;
                break;
        }

        // 真实调起命令
        String curlCommand = "curl -H 'token:de27aefdf8f0392ddab7c2144af67ab0' " +
                "-X POST 'http://etl01:12345/dolphinscheduler/projects/10691104512992/executors/start-process-instance' " +
                "-d 'failureStrategy=END&processDefinitionCode=10691166416992&processInstancePriority=MEDIUM&scheduleTime=&warningGroupId=0&warningType=NONE&startParams={\"comt\":\"" + comt + "\"}'";

        return CommandExecutor.executeWithResult(curlCommand) == 0;
    }
}
