package com.wgzhao.addax.admin.service.impl;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.service.AddaxLogService;
import com.wgzhao.addax.admin.service.AlertService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.EtlJourService;
import com.wgzhao.addax.admin.service.JobContentService;
import com.wgzhao.addax.admin.service.SystemConfigService;
import com.wgzhao.addax.admin.service.TableService;
import com.wgzhao.addax.admin.service.TargetService;
import com.wgzhao.addax.admin.service.TaskQueueManager;
import com.wgzhao.addax.admin.utils.CommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static com.wgzhao.addax.admin.common.Constants.ADDAX_EXECUTE_TIME_OUT_SECONDS;
import static java.lang.Math.max;

@Slf4j
public abstract class AbstractTaskQueueManager implements TaskQueueManager {

    @Autowired
    protected TableService tableService;

    @Autowired
    protected SystemConfigService configService;

    @Autowired
    protected JobContentService jobContentService;

    @Autowired
    protected TargetService targetService;

    @Autowired
    protected DictService dictService;

    @Autowired
    protected AddaxLogService addaxLogService;

    @Autowired
    protected EtlJourService jourService;

    @Autowired
    protected AlertService alertService;


    public TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task) {
        return executeEtlTaskWithConcurrencyControl(task, null);
    }

    // 重载，允许用队列中的 bizDate 覆盖默认 bizDate
    protected TaskResultDto executeEtlTaskWithConcurrencyControl(EtlTable task, LocalDate overrideBizDate) {
        long tid = task.getId();
        long startTime = System.currentTimeMillis();
        try {
            tableService.setRunning(task);
            boolean result = executeEtlTaskLogic(task, overrideBizDate);
            long duration = max((System.currentTimeMillis() - startTime) / 1000, 0);
            log.info("采集任务 {} 执行完成，耗时: {}s, 结果: {}", tid, duration, result);
            task.setDuration(duration);
            if (result) {
                tableService.setFinished(task);
                return TaskResultDto.success("执行成功", duration);
            } else {
                tableService.setFailed(task);
                alertService.sendToWeComRobot(String.format("采集任务执行失败: %s", tid));
                return TaskResultDto.failure("执行失败：Addax 退出非0", duration);
            }
        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.error("采集任务 {} 执行失败，耗时: {}s", tid, duration, e);
            task.setDuration(duration);
            tableService.setFailed(task);
            alertService.sendToWeComRobot(String.format("采集任务执行失败: %s, 错误: %s", tid, e.getMessage()));
            String msg = e.getMessage() == null ? "内部异常" : e.getMessage();
            return TaskResultDto.failure("执行异常: " + msg, duration);
        } finally {
            // runningTaskCount 在 submit 前已增加，在 finally 中由调用方减少
        }
    }

    /**
     * 执行具体的采集逻辑，支持覆盖 bizDate
     */
    protected boolean executeEtlTaskLogic(EtlTable task, LocalDate overrideBizDate) {
        long taskId = task.getId();
        log.info("执行采集任务逻辑: taskId={}, destDB={}, tableName={}", taskId, task.getTargetDb(), task.getTargetTable());
        String job = jobContentService.getJobContent(taskId);
        if (job == null || job.isEmpty()) {
            log.warn("模板未生成, taskId = {}", taskId);
            return false;
        }
        String defaultLogDate = configService.getBizDate(); // yyyyMMdd
        String dw_clt_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String partFormat = task.getPartFormat();
        String bizDateStr;
        if (overrideBizDate != null) {
            // 将 override 的 date 按 partFormat 格式化
            if (partFormat != null && !partFormat.isBlank() && !"yyyyMMdd".equals(partFormat)) {
                bizDateStr = overrideBizDate.format(DateTimeFormatter.ofPattern(partFormat));
            } else {
                bizDateStr = overrideBizDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } else {
            String bizDate = defaultLogDate;
            if (partFormat != null && !partFormat.isBlank() && !partFormat.equals("yyyyMMdd")) {
                bizDate = LocalDate.parse(defaultLogDate, DateTimeFormatter.ofPattern("yyyyMMdd")).format(DateTimeFormatter.ofPattern(partFormat));
            }
            bizDateStr = bizDate;
        }
        log.debug("biz date is {}, dw_clt_date is {}, dw_trade_date is {}", bizDateStr, dw_clt_date, defaultLogDate);
        job = job.replace("${logdate}", bizDateStr).replace("${dw_clt_date}", dw_clt_date).replace("${dw_trade_date}", defaultLogDate);
        if (task.getPartName() != null && !Objects.equals(task.getPartName(), "")) {
            boolean result = targetService.addPartition(taskId, task.getTargetDb(), task.getTargetTable(), task.getPartName(), bizDateStr);
            if (!result) {
                return false;
            }
        }
        File tempFile;
        try {
            // Determine the persistent jobs directory under the parent of program run dir
            String curDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            // The property app.home is set in the service.sh script as the parent directory of the Addax installation
            String jobsDir = Path.of(System.getProperty("app.home")).resolve("job").resolve(curDate) + "/";

            Files.createDirectories(Path.of(jobsDir));
            // Create the temp file in the persistent jobs directory
            tempFile = new File(jobsDir + task.getTargetDb() + "." + task.getTargetTable() + ".json");
            Files.writeString(tempFile.toPath(), job);
        } catch (IOException e) {
            log.error("写入临时文件失败", e);
            return false;
        }
        String logName = String.format("%s.%s_%d.log", task.getTargetDb(), task.getTargetTable(), taskId);
        String cmd = String.format("%s/bin/addax.sh  -p'-DjobName=%d -Dlog.file.name=%s' %s", dictService.getAddaxHome(), taskId, logName, tempFile.getAbsolutePath());
        boolean retCode = executeAddax(cmd, taskId, logName);
        log.debug("采集任务 {} 的日志已写入文件: {}", taskId, logName);
        return retCode;
    }

    protected boolean executeAddax(String command, long tid, String logName) {
        EtlJour etlJour = jourService.addJour(tid, JourKind.COLLECT, command);
        TaskResultDto taskResult = CommandExecutor.executeWithResult(command, ADDAX_EXECUTE_TIME_OUT_SECONDS);
        Path path = Path.of(dictService.getAddaxHome() + "/log/" + logName);
        String logContent = null;
        try {
            logContent = Files.readString(path);
        } catch (IOException e) {
            log.error("读取 Addax 日志文件失败: {}", path, e);
        }
        addaxLogService.insertLog(tid, logContent);
        etlJour.setDuration(taskResult.durationSeconds());
        etlJour.setStatus(true);
        if (!taskResult.success()) {
            log.error("Addax 采集任务 {} 执行失败，退出码: {}", tid, taskResult.message());
            etlJour.setStatus(false);
            etlJour.setErrorMsg(taskResult.message());
        }
        jourService.saveJour(etlJour);
        return taskResult.success();
    }

    @Override
    public boolean addTaskToQueue(long tableId) {
        EtlTable table = tableService.getTable(tableId);
        return table != null && addTaskToQueue(table);
    }
}

