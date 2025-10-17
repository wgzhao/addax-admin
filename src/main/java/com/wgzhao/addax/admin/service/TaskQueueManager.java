package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private int queueSize;

    private int concurrentLimit;

    private BlockingQueue<EtlTable> etlTaskQueue;

    @Autowired
    private DictService dictService;

    @Autowired
    private AddaxLogService addaxLogService;

    @Autowired
    private StatService statService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private TableService tableService;

    @Autowired
    private EtlJourService jourService;

    @Autowired
    private SystemConfigService configService;

    @Autowired private JobContentService jobContentService;
    @Autowired private TargetService targetService;

    // 队列监控标志
    private volatile boolean queueMonitorRunning = false;


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

    @PostConstruct
    public void init()
    {
        configService.loadConfig();
        this.queueSize = configService.getQueueSize();
        this.concurrentLimit = configService.getConcurrentLimit();
        this.etlTaskQueue = new ArrayBlockingQueue<>(queueSize);
        startQueueMonitor();
    }

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

            List<EtlTable> tasks = tableService.getRunnableTasks();
            if (tasks.isEmpty()) {
                return;
            }
            log.info("扫描到 {} 个待采集任务", tasks.size());

            int enqueuedCount = 0;
            int skippedCount = 0;

            for (EtlTable task : tasks) {

                // 尝试将任务加入队列（非阻塞）
                if (etlTaskQueue.offer(task)) {
                    enqueuedCount++;
                    log.debug("任务 {} 已加入队列", task.getId());
                }
                else {
                    skippedCount++;
                    log.warn("队列已满，任务 {} 未能加入队列", task.getId());
                }
            }

            log.info("任务入队完成: 成功入队 {} 个，跳过 {} 个，当前队列大小: {}",
                    enqueuedCount, skippedCount, etlTaskQueue.size());
        }
        catch (Exception e) {
            log.error("扫描和入队采集任务失败", e);
            alertService.sendToWeComRobot("扫描采集任务失败: " + e.getMessage());
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
                EtlTable task = etlTaskQueue.poll(5, TimeUnit.SECONDS);
                if (task == null) {
                    // 队列为空，继续监控
                    continue;
                }

                // 增加运行任务计数
                int currentRunning = runningTaskCount.incrementAndGet();
                log.info("从队列获取任务: {}, 当前并发数: {}/{}",
                        task.getId(), currentRunning, concurrentLimit);

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
     * 返回详细执行结果，包含成功/失败、错误码、消息、耗时等
     */
    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task)
    {
        long tid = task.getId();
        long startTime = System.currentTimeMillis();

        try {
            // 更新任务状态为运行中
            tableService.setRunning(task);
            // 执行具体的采集逻辑（这里先调用现有的采集方法框架）
            boolean result = executeEtlTaskLogic(task);

            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0); // seconds
            log.info("采集任务 {} 执行完成，耗时: {}s, 结果: {}", tid, duration, result);
            task.setDuration(duration);
            // 更新任务状态为成功
            if (result) {
                tableService.setFinished(task);
                return TaskResultDto.success("执行成功", duration);
            }
            else {
                tableService.setFailed(task);
                alertService.sendToWeComRobot(String.format("采集任务执行失败: %s", tid));
                return TaskResultDto.failure("执行失败：Addax 退出非0", duration);
            }
        }
        catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000; // seconds
            log.error("采集任务 {} 执行失败，耗时: {}s", tid, duration, e);
            // 更新任务状态为失败
            task.setDuration(duration);
            tableService.setFailed(task);

            // 发送告警
            alertService.sendToWeComRobot(String.format("采集任务执行失败: %s, 错误: %s", tid, e.getMessage()));
            String msg = e.getMessage() == null ? "内部异常" : e.getMessage();
            return TaskResultDto.failure("执行异常: " + msg, duration);
        }
        finally {
            // 减少运行任务计数
            int currentRunning = runningTaskCount.decrementAndGet();
            log.debug("任务 {} 执行结束，当前并发数: {}", tid, currentRunning);
        }
    }

    /**
     * 执行具体的采集逻辑
     */
    public boolean executeEtlTaskLogic(EtlTable task)
    {
        long taskId = task.getId();

        log.info("执行采集任务逻辑: taskId={}, destDB={}, tableName={}",
                taskId, task.getTargetDb(), task.getTargetTable());
        // 生成已提交任务流水
        String job = jobContentService.getJobContent(taskId);
        if (job == null || job.isEmpty()) {
            log.warn("模板未生成, taskId = {}", taskId);
            return false;
        }

        String logDate = configService.getBizDate(); //yyyyMMdd
        String dw_clt_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        // 分区字段的日期格式需要根据采集表中的 part_format 来进行格式化
        String partFormat = task.getPartFormat();
        String bizDate = logDate;
        if (!partFormat.isBlank() && !partFormat.equals("yyyyMMdd")) {
            bizDate = LocalDate.parse(logDate, DateTimeFormatter.ofPattern("yyyyMMdd"))
                    .format(DateTimeFormatter.ofPattern(partFormat));
        }
        log.info("biz date is {}, dw_clt_date is {}, dw_trade_date is {}", bizDate, dw_clt_date, logDate);
        job = job.replace("${logdate}", bizDate).replace("${dw_clt_date}", dw_clt_date).replace("${dw_trade_date}", logDate);
        if (!Objects.equals(task.getPartName(), "")) {
            // hive 创建分区, 尝试用 hive 命令行创建分区
            boolean result = targetService.addPartition(taskId, task.getTargetDb(), task.getTargetTable(), task.getPartName(), bizDate);
            if (!result) {
                return false;
            }
        }
        // 写入临时文件
        File tempFile;
        try {
            tempFile = File.createTempFile(task.getTargetDb() + "." + task.getTargetTable() + "_", ".json");
            Files.writeString(tempFile.toPath(), job);
        }
        catch (IOException e) {
            log.error("写入临时文件失败", e);
            return false;
        }

        log.debug("采集任务 {} 的Job已写入临时文件: {}", taskId, tempFile.getAbsolutePath());
        // 设定一个日志文件名的名称
        String logName = String.format("addax_%s_%d.log", taskId, System.currentTimeMillis());
        String cmd = String.format("%s/bin/addax.sh  -p'-DjobName=%d -Dlog.file.name=%s' %s", dictService.getAddaxHome(), taskId, logName, tempFile.getAbsolutePath());
        boolean retCode = executeAddax(cmd, taskId, logName);
        log.info("采集任务 {} 的日志已写入文件: {}", taskId, logName);
        return retCode;
    }

    /**
     * 手动添加任务到队列
     */
    public boolean addTaskToQueue(long tid)
    {
        return addTaskToQueue(tableService.getTable(tid));
    }

    public boolean addTaskToQueue(EtlTable task)
    {
        boolean added = etlTaskQueue.offer(task);
        if (added) {
            log.info("手动添加任务到队列: {}", task.getId());
        }
        else {
            log.warn("队列已满，无法添加任务: {}", task.getId());
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

    /**
     * 执行 Addax 采集命令，并处理日志输出
     *
     * @param command Addax 执行命令
     * @param tid 采集表主键
     * @param logName addax 输出的日志文件名
     * @return 程序退出码
     */
    private boolean executeAddax(String command, long tid, String logName)
    {
        log.info("Executing command: {}", command);
        EtlJour etlJour = jourService.addJour(tid, JourKind.COLLECT, command);

        TaskResultDto taskResult = CommandExecutor.executeWithResult(command);
        // 记录日志
        Path path = Path.of(dictService.getAddaxHome() + "/log/" + logName);
        try {
            String logContent = Files.readString(path);
            addaxLogService.insertLog(tid, logContent);
        }
        catch (IOException e) {
            log.warn("Failed to get the addax log content: {}", path);
        }
        etlJour.setDuration(taskResult.getDurationSeconds());
        etlJour.setStatus(true);
        if (!taskResult.isSuccess()) {
            log.error("Addax 采集任务 {} 执行失败，退出码: {}", tid, taskResult.getMessage());
            etlJour.setStatus(false);
            etlJour.setErrorMsg(taskResult.getMessage());
        }
        jourService.saveJour(etlJour);
        return taskResult.isSuccess();
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
    private void processAddaxStatistics(long tid, LinkedList<String> lastLines)
    {
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
        String jobStart = stats.get("Job start  at").replace(" ", "T");
        String jobEnd = stats.get("Job end    at").replace(" ", "T");

        EtlStatistic statistic = new EtlStatistic();
        statistic.setTid(tid);
        statistic.setRunDate(LocalDate.now());
        statistic.setStartAt(LocalDateTime.parse(jobStart));
        statistic.setEndAt(LocalDateTime.parse(jobEnd));
        long jobTook = Long.parseLong(stats.get("Job took secs").replace("s", ""));
        statistic.setTakeSecs(jobTook);
        long totalBytes = Long.parseLong(stats.get("Total   bytes"));
        statistic.setTotalBytes(totalBytes);
        long numberOfRec = Long.parseLong(stats.get("Number of rec"));
        statistic.setTotalRecs(numberOfRec);
        long failedRecord = Long.parseLong(stats.get("Failed record"));
        statistic.setTotalErrors(failedRecord);
        long averageBps = totalBytes / (jobTook == 0 ? 1 : jobTook);
        statistic.setByteSpeed(averageBps);
        long averageRps = numberOfRec / (jobTook == 0 ? 1 : jobTook);
        statistic.setRecSpeed(averageRps);

        if (statService.saveOrUpdate(statistic)) {
            log.info("Addax 采集统计信息已插入 tb_addax_statistic 表: {}", statistic);
        }
    }

    public BlockingQueue<EtlTable> getEtlQueue()
    {
        return this.etlTaskQueue;
    }
}
