package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.EtlTask;
import com.wgzhao.addax.admin.model.TbAddaxStatistic;
import com.wgzhao.addax.admin.utils.FileUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;

/**
 * 采集任务队列管理器
 * 负责管理固定长度（100）的采集任务队列，控制30个并发采集程序
 */
@Component
@Slf4j
public class TaskQueueManager
{

    private final int queueSize = 100;

    private final int concurrentLimit = 30;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DictService dictService;

    @Autowired
    private AddaxLogService addaxLogService;

    @Autowired
    private AddaxStatService statService;

    @Autowired
    private AlertService alertService;


    // 采集任务队列 - 固定长度100
    private final BlockingQueue<EtlTask> etlTaskQueue = new ArrayBlockingQueue<>(100);

    // 当前执行中的采集任务数
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);

    // 采集任务监控线程池
    private final ExecutorService queueMonitorExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "etl-queue-monitor");
        t.setDaemon(true);
        return t;
    });

    // 采集任务执行线程池
    private final ExecutorService etlExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "etl-worker");
        t.setDaemon(true);
        return t;
    });

    // 队列监控标志
    private volatile boolean queueMonitorRunning = false;

    /**
     * 扫描并将采集任务加入队列
     * 其逻辑为:
     * 1. 先检查当前时间是否小于切日时间(SWITCH_TIME),如果小于，则 扫描tb_imp_etl表中flag字段为N的记录
     * 2. 否则，需要检查任务设定的采集时间是否小于当前时间
     * 比如，假定是T 日下午 16：:30切日，且采集时间设定为 02:50，则表示需要在 T+1 日后的 02:50 之后才能采集
     * 当如果采集时间设定的为 14:30，则需要 T+1 日后的 14:30 之后才能采集
     *
     */
    public void scanAndEnqueueEtlTasks()
    {
        try {
            // 查询需要采集的任务 - 扫描tb_imp_etl表中flag字段为N的记录
            LocalTime switchTime = dictService.getSwitchTimeAsTime();
            LocalTime currentTime = LocalDateTime.now().toLocalTime();
            String sql = """
                    select t.tid, t.dest_tablename, 'ods' || lower(t.sou_sysid ) as dest_db from tb_imp_etl  t
                    join tb_imp_db d
                    on t.sou_sysid  = d.db_id_etl
                    where t.flag = 'N' and t.retry_cnt > 0 and t.bupdate  = 'N' and t.bcreate = 'N' and d.bvalid  = 'Y'
                    """;
            if (currentTime.isAfter(switchTime)) {
                // 当前时间大于切日时间，则需要检查采集时间
                sql += " and d.db_start > '" + switchTime + "' and d.db_start < current_time";
            }
            List<Map<String, Object>> etlTasks = jdbcTemplate.queryForList(sql);
            log.info("扫描到 {} 个待采集任务", etlTasks.size());

            int enqueuedCount = 0;
            int skippedCount = 0;

            for (Map<String, Object> taskData : etlTasks) {
                String taskId = String.valueOf(taskData.get("tid"));
                String taskType = "etl"; // 采集任务类型

                EtlTask etlTask = new EtlTask(taskId, taskType, taskData);

                // 尝试将任务加入队列（非阻塞）
                if (etlTaskQueue.offer(etlTask)) {
                    enqueuedCount++;
                    log.debug("任务 {} 已加入队列", taskId);
                }
                else {
                    skippedCount++;
                    log.warn("队列已满，任务 {} 未能加入队列", taskId);
                }
            }

            log.info("任务入队完成: 成功入队 {} 个，跳过 {} 个，当前队列大小: {}",
                    enqueuedCount, skippedCount, etlTaskQueue.size());
        }
        catch (Exception e) {
            log.error("扫描和入队采集任务失败", e);
            alertService.sendToWecomRobot("扫描采集任务失败: " + e.getMessage());
        }
    }

    /**
     * 启动队列监控器
     */
    public void startQueueMonitor()
    {
        if (!queueMonitorRunning) {
            queueMonitorRunning = true;
            queueMonitorExecutor.submit(this::queueMonitorLoop);
            log.info("采集任务队列监控器已启动，队列容量: {}, 并发限制: {}", queueSize, concurrentLimit);
        }
        else {
            log.info("队列监控器已在运行中");
        }
    }

    /**
     * 队列监控循环 - 从队列获取任务并控制并发执行
     */
    private void queueMonitorLoop()
    {
        log.info("队列监控器开始运行，并发限制: {}", concurrentLimit);

        while (queueMonitorRunning) {
            try {
                // 检查当前并发数是否达到限制
                if (runningTaskCount.get() >= concurrentLimit) {
                    // 并发已满，等待一段时间后重试
                    Thread.sleep(1000);
                    continue;
                }

                // 从队列中获取任务（阻塞等待，最多等待5秒）
                EtlTask task = etlTaskQueue.poll(5, TimeUnit.SECONDS);
                if (task == null) {
                    // 队列为空，继续监控
                    continue;
                }

                // 增加运行任务计数
                int currentRunning = runningTaskCount.incrementAndGet();
                log.info("从队列获取任务: {}, 当前并发数: {}/{}",
                        task.getTaskId(), currentRunning, concurrentLimit);

                // 提交任务到执行线程池
                etlExecutor.submit(() -> executeEtlTaskWithConcurrencyControl(task));
            }
            catch (InterruptedException e) {
                log.info("队列监控器被中断");
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception e) {
                log.error("队列监控器异常", e);
                // 发生异常时等待一段时间再继续
                try {
                    Thread.sleep(5000);
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("队列监控器已停止");
    }

    /**
     * 执行采集任务并控制并发
     */
    public boolean executeEtlTaskWithConcurrencyControl(EtlTask task)
    {
        String taskId = task.getTaskId();
        long startTime = System.currentTimeMillis();

        try {
            log.info("开始执行采集任务: {}", taskId);

            // 更新任务状态为运行中
            jdbcTemplate.update("update tb_imp_etl set flag = 'R' , start_time = current_timestamp where tid = ?", taskId);
            // 执行具体的采集逻辑（这里先调用现有的采集方法框架）
            boolean result = executeEtlTaskLogic(task);

            long duration = (System.currentTimeMillis() - startTime) / 1000; // seconds
            log.info("采集任务 {} 执行完成，耗时: {}ms, 结果: {}", taskId, duration, result);

            // 更新任务状态为成功
            if (result) {
                jdbcTemplate.update("update tb_imp_etl set flag = 'Y' , end_time = current_timestamp, runtime = ? where tid = ?", duration, taskId);
                return true;
            }
            else {
                jdbcTemplate.update("update tb_imp_etl set flag = 'E' , end_time = current_timestamp, runtime = ? where tid = ?", duration, taskId);
                alertService.sendToWecomRobot(String.format("采集任务执行失败: %s", taskId));
                return false;
            }
        }
        catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000; // seconds
            log.error("采集任务 {} 执行失败，耗时: {}s", taskId, duration, e);

            // 更新任务状态为失败
            jdbcTemplate.update("update tb_imp_etl set flag = 'E' , end_time = current_timestamp, runtime = ? where tid = ?", duration, taskId);

            // 发送告警
            alertService.sendToWecomRobot(String.format("采集任务执行失败: %s, 错误: %s", taskId, e.getMessage()));
            return false;
        }
        finally {
            // 减少运行任务计数
            int currentRunning = runningTaskCount.decrementAndGet();
            log.debug("任务 {} 执行结束，当前并发数: {}", taskId, currentRunning);
        }
    }

    /**
     * 执行具体的采集逻辑（框架方法，具体逻辑待补充）
     */
    public boolean executeEtlTaskLogic(EtlTask task)
    {
        String taskId = task.getTaskId();
        Map<String, Object> taskData = task.getTaskData();

        log.info("执行采集任务逻辑: taskId={}, destDB={}, tableName={}",
                taskId, taskData.get("dest_db"), taskData.get("dest_tablename"));

        String job = jdbcTemplate.queryForObject("select job from tb_imp_etl_job where tid = '" + task.getTaskId() + "'", String.class);
        if (job == null || job.isEmpty()) {
            log.warn("模板未生成, taskId = {}", taskId);
            return false;
        }

        String logdate = dictService.getBizDate();
        String dw_clt_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        job = job.replace("${logdate}", logdate).replace("${dw_clt_date}", dw_clt_date).replace("${dw_trade_date}", logdate);

        // hive 创建分区
        //TODO: 创建分区
//        try {
//            hivePartitionManager.addPartition(taskData.get("dest_db").toString(),
//                    taskData.get("dest_tablename").toString(),
//                    Map.of("logdate", logdate));
//        }
//        catch (HiveException e) {
//            log.error("创建Hive分区失败: {}", e.getMessage(), e);
//            return false;
//        }
        // 写入临时文件
        String tmpFile;
        try {
            tmpFile = FileUtils.writeToTempFile(taskData.get("dest_db") + "." + taskData.get("dest_tablename")  + "_", job);
        }
        catch (IOException e) {
            log.error("写入临时文件失败", e);
            return false;
        }

        log.debug("采集任务 {} 的Job已写入临时文件: {}", taskId, tmpFile);
        String cmd = dictService.getAddaxHome() + "/bin/addax.sh -p'-DjobName=" + taskId + "' " + tmpFile;
        String logfile = "addax_" + taskId + "_" + System.currentTimeMillis() + ".log";
        int retCode = executeAddax(cmd, taskId);
        log.info("采集任务 {} 的日志已写入文件: {}", taskId, logfile);
        return retCode == 0;
    }

    /**
     * 手动添加任务到队列
     */
    public boolean addTaskToQueue(String taskId, String taskType, Map<String, Object> taskData)
    {
        EtlTask task = new EtlTask(taskId, taskType, taskData);
        return addTaskToQueue(task);
    }

    public boolean addTaskToQueue(EtlTask task) {
        boolean added = etlTaskQueue.offer(task);
        if (added) {
            log.info("手动添加任务到队列: {}", task.getTaskId());
        }
        else {
            log.warn("队列已满，无法添加任务: {}", task.getTaskId());
        }
        return added;
    }

    /**
     * 停止队列监控器
     */
    public void stopQueueMonitor()
    {
        queueMonitorRunning = false;
        log.info("队列监控器停止信号已发送");
    }

    /**
     * 获取队列状态信息
     */
    public Map<String, Object> getQueueStatus()
    {
        Map<String, Object> status = new HashMap<>();
        status.put("queueSize", etlTaskQueue.size());
        status.put("queueCapacity", queueSize);
        status.put("runningTaskCount", runningTaskCount.get());
        status.put("concurrentLimit", concurrentLimit);
        status.put("queueMonitorRunning", queueMonitorRunning);
        status.put("timestamp", LocalDateTime.now());
        return status;
    }

    /**
     * 清空队列
     */
    public int clearQueue()
    {
        int size = etlTaskQueue.size();
        etlTaskQueue.clear();
        log.info("已清空队列，清除了 {} 个任务", size);
        return size;
    }

    /**
     * 应用关闭时的清理工作
     */
    @PreDestroy
    public void shutdown()
    {
        log.info("开始关闭采集任务队列管理器...");

        // 停止队列监控
        stopQueueMonitor();

        // 关闭线程池
        queueMonitorExecutor.shutdown();
        etlExecutor.shutdown();

        try {
            // 等待线程池关闭
            if (!queueMonitorExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                queueMonitorExecutor.shutdownNow();
            }
            if (!etlExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                etlExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            queueMonitorExecutor.shutdownNow();
            etlExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("采集任务队列管理器已关闭");
    }

    private int executeAddax(String command, String tid)
    {
        Process process = null;
        log.info("Executing command: {}", command);
        try {
            process = Runtime.getRuntime().exec(new String[] {"sh" , "-c", command});
            StringBuilder sb = new StringBuilder();
            // 将输出重定向到标准输出
            LinkedList<String> lastLines = new LinkedList<>();
            final int MAX_LINES = 9;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (lastLines.size() >= MAX_LINES) {
                        lastLines.removeFirst();
                    }
                    lastLines.add(line);
                }
                addaxLogService.insertLog(tid, sb.toString());
                if (!lastLines.isEmpty()) {
                    processAddaxStatistics(tid, lastLines);
                }
            }

            // 将错误输出重定向到标准错误
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.error(line);
                }
            }
            return process.waitFor();
        }
        catch (IOException | InterruptedException e) {
            if (process != null) {
                process.destroy();
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }
    }

    /**
     * 分析 Addax 采集的最后 8 行信息，提取统计信息，并插入到 tb_addax_sta 表中
     * 最后 8 行的信息类似如下：
     * <p>
     * Job start  at             : 2025-09-16 09:00:50
     * Job end    at             : 2025-09-16 09:01:00
     * Job took secs             :                  9s
     * Total   bytes             :               41841
     * Average   bps             :            6.81KB/s
     * Average   rps             :             74rec/s
     * Number of rec             :                 449
     * Failed record             :                   0
     *
     * @param tid 采集表主键
     * @param lastLines 最后的统计信息
     */
    private void processAddaxStatistics(String tid, LinkedList<String> lastLines) {
        Map<String, String> stats = new HashMap<>();
        for (String line : lastLines) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                stats.put(parts[0].trim(), parts[1].trim());
            }
        }
        if (stats.size() < 8) {
            log.warn("无法解析 Addax 统计信息，行数不足: {}", stats);
            return;
        }
        if (!stats.containsKey("Job start  at") || stats.get("Job start  at").isEmpty()) {
            log.error("无法解析 Addax 统计信息，缺少 Job start  at 字段: {}", stats);
            return;
        }
        String jobStart =  stats.get("Job start  at").replace(" ", "T");
        String jobEnd = stats.get("Job end    at").replace(" ", "T");

        TbAddaxStatistic statistic = new TbAddaxStatistic();
        statistic.setTid(tid);
        statistic.setRunDate(LocalDate.now());
        statistic.setStartAt(LocalDateTime.parse(jobStart));
        statistic.setEndAt(LocalDateTime.parse(jobEnd));
        int jobTook = Integer.parseInt(stats.get("Job took secs").replace("s", ""));
        statistic.setTakeSecs(jobTook);
        int totalBytes = Integer.parseInt(stats.get("Total   bytes"));
        statistic.setTotalBytes(totalBytes);
        int numberOfRec = Integer.parseInt(stats.get("Number of rec"));
        statistic.setTotalRecs(numberOfRec);
        int failedRecord = Integer.parseInt(stats.get("Failed record"));
        statistic.setTotalErrors(failedRecord);
        int averageBps = totalBytes / jobTook;
        statistic.setByteSpeed(averageBps);
        int averageRps = max(numberOfRec / jobTook, 1);
        statistic.setRecSpeed(averageRps);

        if (statService.saveOrUpdate(statistic)) {
            log.info("Addax 采集统计信息已插入 tb_addax_statistic 表: {}", statistic);
        }
    }
}
