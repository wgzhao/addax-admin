package com.wgzhao.addax.admin.utils;

import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * 工具类，提供各种共享功能
 */
public class SpAloneUtils {

    private static final String c_sql = RedisUtils.get("com.inicmd");

    /**
     * 记录系统日志
     * @param message 日志内容
     */
    public static void syslog(String message) {
        String logPath = RedisUtils.get("path.runlog") + "/tuna_syslog_" + DateUtils.getCurrentDate("yyyyMMdd") + "_00.log";
        String logEntry = DateUtils.getCurrentDateTime("yyyy-MM-dd_HH:mm:ss") + "|" +
                CommandExecutor.getHostname() + "|" +
                CommandExecutor.getPid() + "|" +
                message;

        try {
            FileUtils.appendToFile(logPath, logEntry);
        } catch (IOException e) {
            System.err.println("Error writing to syslog: " + e.getMessage());
        }
    }

    /**
     * 等待可用的队列索引
     * @param prefix 队列前缀
     * @param maxNum 最大并发数
     * @return 可用的队列索引
     */
    public static String waitIdxResult(String prefix, int maxNum) {

        while (true) {
            for (int idx = 1; idx <= maxNum; idx++) {
                String flagName = prefix + "_" + idx;
                if ("1".equals(RedisUtils.flagAdd(flagName))) {
                    return String.valueOf(idx);
                }
            }

            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "0";
            }
        }
    }

    /**
     * 执行SQL文件
     * @param sqlFile SQL文件路径
     * @param parallelNum 并发数，默认为1
     * @return 执行结果，0表示成功
     */
    public static boolean runSql(String sqlFile, int parallelNum) {
        if (parallelNum == 0) {
            parallelNum = 1;
        }

        String parallelName = "runsql_" + java.util.UUID.randomUUID().toString().replace("-", "");
        System.out.println("\n" + DateUtils.getCurrentDateTime() + ":并发数：" + parallelNum +
                ",并发任务名称：" + parallelName + ",设置rds：" + RedisUtils.set(parallelName, "0"));

        System.out.println("content=[" + FileUtils.readFileContent(sqlFile) + "]\n");

        if (FileUtils.countLines(sqlFile) < 4) {
            System.out.println("原始文件" + sqlFile + "没有需要执行的代码");
            return true;
        }

        // 获取原始SQL文件中的前三列重要信息
        String dbUser = "";
        String dbPass = "";
        String dbUrl = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile))) {
            dbUser = reader.readLine();
            dbPass = reader.readLine();
            dbUrl = reader.readLine();
        } catch (IOException e) {
            System.err.println("Error reading SQL file: " + e.getMessage());
            return false;
        }

        // 按顺序读取原始SQL文件实际执行代码
        String tmpFile = "/tmp/" + parallelName + ".txt";
        CommandExecutor.execute("sed -n '4,10000p' " + sqlFile + " >" + tmpFile);

        String[] lines = FileUtils.readFileContent(tmpFile).split("\n");

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();

        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            final String sqlLine = line;
            String finalDbUrl = dbUrl;
            String finalDbUser = dbUser;
            String finalDbPass = dbPass;
            int finalParallelNum = parallelNum;
            executor.submit(() -> {
                String rc = waitIdxResult(parallelName, finalParallelNum);
                String fname = parallelName + "_" + rc;

                System.out.println(DateUtils.getCurrentDateTime() + ":[" + fname + "]开始执行[" + sqlLine + "]");
                int result = CommandExecutor.executeWithResult(RedisUtils.get("path.bin") + "/jdbc2console.sh -U \"" +
                        finalDbUrl + "\" -u \"" + finalDbUser + "\" -p \"" + finalDbPass + "\" \"" +
                        sqlLine + "\" 2>&1");

                if (result != 0) {
                    RedisUtils.set(parallelName, String.valueOf(result));
                }

                System.out.println(DateUtils.getCurrentDateTime() + ":[" + fname + "]执行结束[" + sqlLine + "]，执行结果[" +
                        result + "]，删除标志：" + RedisUtils.flagRemove(fname));
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        CommandExecutor.execute("rm -f " + tmpFile);

        System.out.println(DateUtils.getCurrentDateTime() + ":全部任务执行完毕,执行结果：" + RedisUtils.get(parallelName));

        String rc = RedisUtils.get(parallelName);
        RedisUtils.delete(parallelName);

        return "0".equals(rc);
    }

    /**
     * 在数据库源库执行语句
     * @param sysid 系统ID
     * @param sql SQL语句
     * @return 执行结果
     */
//    public static String sdb(String sysid, String sql) {
//        String s = "\"" + sql + "\"";
//        String command = RedisUtils.get("path.bin") + "/jdbc2console.sh -f MySQL $(" + c_sql +
//                " \"select db_conn from stg01.vw_imp_system where sysid='" + sysid + "'")
//        return CommandExecutor.executeForOutput(command);
//    }

}
