package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.utils.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SpAloneService {
    // 通用变量
    private static final String rootdir = RedisUtils.get("path.infalog");
    private static final String curpath = RedisUtils.get("path.bin");
    private static boolean v_rmlog = false;

    private static final String v_today = DateUtils.getCurrentDate("yyyyMMdd");
    private static final String v_yest = DateUtils.getYesterdayDate("yyyyMMdd");
    private static final String c_ckh = RedisUtils.get("com.ckhcmd");
    private static final String c_presto = RedisUtils.get("com.prestocmd");
    private static final String c_allsql = RedisUtils.get("com.prestoall");
    private static final String c_sql = RedisUtils.get("com.inicmd");
    private static final String c_sql3 = RedisUtils.get("com.iniout");

    private static String logfile;


    @Resource
    private CacheUtil cacheUtil;

    public  void spAlone(String... args) {
        if (args.length == 0) {
            System.out.println("请提供操作类型参数");
            return;
        }

        // 日志文件名
        String logprex;
        if ("ds_etl".equals(args[0]) || "sp_etl".equals(args[0])) {
            logprex = args[0] + "_" + args[1];
        } else {
            logprex = args[0];
        }

        logfile = RedisUtils.get("path.runlog") + "/tuna_" + logprex + "_" +
                DateUtils.getCurrentDate("yyyyMMdd_HHmmss") + "_" +
                CommandExecutor.getHostname().split("\\.")[0] + "_" +
                ProcessHandle.current().pid() + ".log";

        // 记录开始信息
        tip(args[0], "1", String.join(" ", args));
        LogUtils.appendToFile(logfile, "######curpath=" + curpath + ",shname=" + args[0] +
                ",logfile=" + logfile + ",v_today=" + v_today +
                ",v_yest=" + v_yest + ",param=[" + String.join(" ", args) + "]######\n");

        // 检查系统是否暂停
        if ("Y".equals(RedisUtils.get("com.halt"))) {
            String msg = DateUtils.getCurrentDateTime() + ":系统暂停服务!!sp_alone:" + args[0] + "无法执行";
            System.out.println(msg);
            LogUtils.appendToFile(logfile, msg);
            CommandExecutor.execute(c_sql + " \"begin sp_sms('" + msg + "','1','110');end;\"");
        } else {
            // 根据参数调用相应的方法
            boolean success = dispatchCommand(args);
            if (!success) {
                CommandExecutor.execute(c_sql + " \"begin sp_sms('sp_alone:" + args[0] +
                        "[" + (args.length > 1 ? args[1] : "") + "]执行失败，请速速排查','1','110');end;\"");
            }
        }

        // 记录结束信息
        tip(args[0], "2", String.join(" ", args));
        LogUtils.appendToFile(logfile, "\n\n\n展示日志文件内容\n" + LogUtils.readFile(logfile));

        // 删除空的日志文件
        if (LogUtils.countLines(logfile) <= 6 || v_rmlog) {
            LogUtils.deleteFile(logfile);
        }
    }

    /**
     * 根据命令类型分发到对应的处理方法
     */
    private  boolean dispatchCommand(String[] args) {
        try {
            switch (args[0]) {
                case "wait_idx":
                    return waitIdx(args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                case "runsql":
                    return runSql(args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                case "sdb":
                    return sdb(args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                case "syslog":
                    return syslog(args.length > 1 ? args[1] : "");
                case "autobak":
                    return autoBak();
                case "plan_start":
                    return planStart();
                case "sp_start":
                    return spStart();
                case "sp_init":
                    return spInit();
                case "sp_etl":
                    return spEtl(args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                case "ds_etl":
                    return dsEtl(args.length > 1 ? args[1] : "");
                case "judge_init":
                    return judgeInit(args.length > 1 ? args[1] : "");
                case "judge_etl":
                    return judgeEtl(args.length > 1 ? args[1] : "");
                case "soutab_start":
                    return soutabStart();
                case "soutab_etl":
                    return soutabEtl(args.length > 1 ? args[1] : "");
                case "updt_param":
                    return updtParam();
                case "start_wkf":
                    return startWkf(args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                case "dschk":
                    return dschk();
                case "syschk":
                    return syschk();
                case "etl_end":
                    return etlEnd(args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                default:
                    System.out.println("未知命令: " + args[0]);
                    return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 循环判断队列中是否还有可用的，模拟阻塞
     * 返回可以执行的队列编号，从1开始
     */
    private static boolean waitIdx(String param1, String param2) {
        if (param2.isEmpty()) {
            return false;
        }

        int maxIdx = Integer.parseInt(param2);
        while (true) {
            for (int idx = 1; idx <= maxIdx; idx++) {
                if ("1".equals(RedisUtils.flagAdd(param1 + "_" + idx))) {
                    System.out.println(idx);
                    return true;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * 执行指定的SQL文件,适用于针对某一个库执行多条SQL语句
     * 参数1：需要执行的SQL文件(文件前三行分别为：账号、密码、数据库连接串)
     * 参数2：并发数，非必填，默认1
     */
    private static boolean runSql(String sqlFile, String parallelNum) {
        int paral_num = 1;
        if (!parallelNum.isEmpty()) {
            paral_num = Integer.parseInt(parallelNum);
        }

        String paral_name = "runsql_" + java.util.UUID.randomUUID().toString().replace("-", "");
        System.out.println("\n" + DateUtils.getCurrentDateTime() + ":并发数：" + paral_num +
                ",并发任务名称：" + paral_name + ",设置rds：" + RedisUtils.set(paral_name, "0"));

        System.out.println("content=[" + LogUtils.readFile(sqlFile) + "]\n");

        if (LogUtils.countLines(sqlFile) < 4) {
            System.out.println("原始文件" + sqlFile + "没有需要执行的代码");
            return true;
        }

        // 获取原始SQL文件中的前三列重要信息
        String db_user = LogUtils.readLine(sqlFile, 1);
        String db_pass = LogUtils.readLine(sqlFile, 2);
        String db_url = LogUtils.readLine(sqlFile, 3);

        // 按顺序读取原始SQL文件实际执行代码
        String tmpfile = "/tmp/" + paral_name + ".txt";
        CommandExecutor.execute("sed -n '4,10000p' " + sqlFile + " >" + tmpfile);

        ExecutorService executor = Executors.newFixedThreadPool(paral_num);

        for (String line : LogUtils.readLines(tmpfile)) {
            if (line.trim().isEmpty()) {
                continue;
            }

            String rc = waitIdxResult(paral_name, String.valueOf(paral_num));
            String fname = paral_name + "_" + rc;

            executor.submit(() -> {
                System.out.println(DateUtils.getCurrentDateTime() + ":[" + fname + "]开始执行[" + line + "]");
                int result = CommandExecutor.executeWithResult(curpath + "/jdbc2console.sh -U \"" + db_url +
                        "\" -u \"" + db_user + "\" -p \"" + db_pass + "\" \"" + line + "\"");
                if (result != 0) {
                    RedisUtils.set(paral_name, String.valueOf(result));
                }
                System.out.println(DateUtils.getCurrentDateTime() + ":[" + fname + "]执行结束[" + line +
                        "],执行结果[" + result + "],删除标志：" + RedisUtils.flagRemove(fname));
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        LogUtils.deleteFile(tmpfile);
        System.out.println(DateUtils.getCurrentDateTime() + ":全部任务执行完毕,执行结果：" + RedisUtils.get(paral_name));

        String rc = RedisUtils.get(paral_name);
        RedisUtils.delete(paral_name);
        return "0".equals(rc);
    }

    // waitIdx的辅助方法，返回结果而不是布尔值
    private static String waitIdxResult(String param1, String param2) {
        if (param2.isEmpty()) {
            return "";
        }

        int maxIdx = Integer.parseInt(param2);
        while (true) {
            for (int idx = 1; idx <= maxIdx; idx++) {
                if ("1".equals(RedisUtils.flagAdd(param1 + "_" + idx))) {
                    return String.valueOf(idx);
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            }
        }
    }

    /**
     * 在数据库源库执行语句
     */
    private static boolean sdb(String sysid, String sql) {
        String dbConn = CommandExecutor.executeForOutput(c_sql + " \"select db_conn from vw_imp_system where sysid='" + sysid + "'\"");
        return CommandExecutor.executeWithResult(curpath + "/jdbc2console.sh -f MySQL " + dbConn + " \"" + sql + "\"") == 0;
    }

    /**
     * 记录运行日志(时间|机器名|进程编号|日志内容)
     */
    private static boolean syslog(String message) {
        String logEntry = DateUtils.getCurrentDateTime("yyyy-MM-dd_HH:mm:ss") + "|" +
                CommandExecutor.getHostname() + "|" + ProcessHandle.current().pid() + "|" + message;
        LogUtils.appendToFile(RedisUtils.get("path.runlog") + "/tuna_syslog_" + v_today + "_00.log", logEntry);
        return true;
    }

    /**
     * 该脚本为通用脚本，为所有脚本提供统一可控的日志提示
     */
    private static void tip(String shellName, String status, String message) {
        String shname = RedisUtils.get("shname." + shellName);
        String shsts = RedisUtils.get("com.shsts_" + status);

        if (shname == null || shname.isEmpty()) {
            shname = "未知名称:" + shellName;
        }
        if (shsts == null || shsts.isEmpty()) {
            shsts = "未知状态:" + status;
        }

        String ipAddress = CommandExecutor.executeForOutput("ifconfig | grep \"188\\.175\\.\" | awk '{print $2}'");
        String output = "--------[<b>" + shname + "</b>]***" + shsts + "***[当前时间:" +
                DateUtils.getCurrentDateTime() + "][服务器:" + CommandExecutor.getHostname() +
                "{" + ipAddress + "}][" + message + "]--------\n";

        System.out.println(output);
        LogUtils.appendToFile(logfile, output);
    }

    // 以下是其他函数的实现，每个函数对应原脚本中的一个函数

    private static boolean autoBak() {
        // 注释：自动备份功能，需要另外实现
        System.out.println("执行自动备份");
        return true;
    }

    private static boolean planStart() {
        // 注释：计划任务主控制，需要另外实现
        System.out.println("执行计划任务主控制");
        return true;
    }

    private static boolean spStart() {
        // 注释：启动任务的并发入口，需要另外实现
        System.out.println("执行任务并发入口");
        return true;
    }

    private static boolean spInit() {
        // 注释：采集,SP,数据服务的总入口，需要另外实现
        System.out.println("执行采集,SP,数据服务的总入口");
        return true;
    }

    private static boolean spEtl(String taskId, String mode) {
        // 注释：采集,SP,计划任务的具体执行，需要另外实现
        System.out.println("执行采集,SP,计划任务: " + taskId + ", 模式: " + mode);
        return true;
    }

    private static boolean dsEtl(String dsId) {
        // 注释：数据服务的具体执行，需要另外实现
        System.out.println("执行数据服务: " + dsId);
        return true;
    }

    private static boolean judgeInit(String param) {
        // 注释：采集前置任务:备份采集表，需要另外实现
        System.out.println("执行采集前置任务: " + param);
        return true;
    }

    private static boolean judgeEtl(String param) {
        // 注释：判断标志的具体实现，需要另外实现
        System.out.println("执行判断标志: " + param);
        return true;
    }

    private static boolean soutabStart() {
        // 注释：源表信息获取启动，需要另外实现
        System.out.println("执行源表信息获取启动");
        return true;
    }

    private static boolean soutabEtl(String dbConn) {
        // 注释：获取表的字段信息，需要另外实现
        System.out.println("执行获取表字段信息: " + dbConn);
        return true;
    }

    private static boolean updtParam() {
        // 注释：切日工作流，整体替换WF_PARAM_FILE，需要另外实现
        System.out.println("执行切日工作流");
        return true;
    }

    private static boolean startWkf(String type, String param) {
        // 注释：统一调起工作流，需要另外实现
        System.out.println("执行调起工作流: " + type + ", 参数: " + param);
        return true;
    }

    private static boolean dschk() {
        // 注释：检测ds调度工具是否正常，需要另外实现
        System.out.println("执行检测ds调度工具");
        return true;
    }

    private static boolean syschk() {
        // 注释：系统检测，需要另外实现
        System.out.println("执行系统检测");
        return true;
    }

    private static boolean etlEnd(String sysid, String param) {
        // 注释：ODS采集完后，需要执行的后续操作，需要另外实现
        System.out.println("执行ODS采集后续操作: " + sysid + ", 参数: " + param);
        return true;
    }
}