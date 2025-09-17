package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.TbImpEtl;
import com.wgzhao.addax.admin.model.TbImpEtlJob;
import com.wgzhao.addax.admin.repository.TbImpEtlJobRepo;
import com.wgzhao.addax.admin.repository.TbImpEtlRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采集任务入口管理器
 * 提供修改后的executePlanStart方法，集成队列管理功能
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService
{

    private final TaskQueueManager queueManager;

    private final JdbcTemplate jdbcTemplate;
    private final TbImpEtlRepo tbImpEtlRepo;
    private final TbImpEtlJobRepo tbImpEtlJobRepo;
    private final SystemConfigService configService;

    /**
     * 计划任务主控制 - 基于队列的采集任务管理
     * 这是采集任务的入口方法，负责扫描tb_imp_etl表并管理采集队列
     */
    public String executePlanStartWithQueue()
    {

        log.info("基于队列的计划任务主控制开始执行");

        // 启动队列监控器（如果还未启动）
        queueManager.startQueueMonitor();

        // 处理其他类型任务（judge等非ETL任务）
        processNonEtlTasks();

        // 扫描tb_imp_etl表中flag字段为N的记录并加入队列
        queueManager.scanAndEnqueueEtlTasks();

        log.info("计划任务主控制执行完毕，队列状态: {}", queueManager.getQueueStatus());
        return "计划任务执行完成，采集任务已加入队列";
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    private void processNonEtlTasks()
    {
        // 如果当前时间是在切日时间附近，则开始做切日处理
        // 1. 把所有有效的采集任务的 flag 字段设置为 'N'，以便重新采集
        String currTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String switchTime = configService.getSwitchTime();
        // 只需要比较分钟和小时即可
        if (currTime.equals(switchTime)) {
            log.info("当前时间 {} 在切日时间 {} 附近，开始重置所有采集任务的 flag 字段为 'N'", currTime, switchTime);
            tbImpEtlRepo.resetAllEtlFlags();
        }
    }

    /**
     * 手动添加采集任务到队列
     */
    public boolean addEtlTaskToQueue(String etlId)
    {
        try {
            // 查询任务详情
            String sql = """
                    SELECT tid, sou_sysid, sou_tablename, etl_type, priority, create_time
                    FROM tb_imp_etl
                    WHERE etl_id = ? AND flag = 'N'
                    """;

            List<Map<String, Object>> taskList = jdbcTemplate.queryForList(sql, etlId);
            if (taskList.isEmpty()) {
                log.warn("未找到可添加的任务: {}", etlId);
                return false;
            }

            Map<String, Object> taskData = taskList.get(0);
            return queueManager.addTaskToQueue(etlId, "etl", taskData);
        }
        catch (Exception e) {
            log.error("手动添加任务到队列失败: {}", etlId, e);
            return false;
        }
    }

    /**
     * 获取采集任务队列的详细状态
     */
    public Map<String, Object> getEtlQueueStatus()
    {
        Map<String, Object> detailedStatus = new HashMap<>(queueManager.getQueueStatus());

        try {
            // 添加数据库中待处理任务数量
            String sql = "SELECT COUNT(*) as pending_count FROM tb_imp_etl WHERE flag = 'N'";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            if (!result.isEmpty()) {
                detailedStatus.put("pendingInDatabase", result.get(0).get("pending_count"));
            }

            // 添加正在运行的任务数量
            sql = "SELECT COUNT(*) as running_count FROM tb_imp_etl WHERE flag = 'R'";
            result = jdbcTemplate.queryForList(sql);
            if (!result.isEmpty()) {
                detailedStatus.put("runningInDatabase", result.get(0).get("running_count"));
            }
        }
        catch (Exception e) {
            log.error("获取数据库任务状态失败", e);
        }

        return detailedStatus;
    }

    /**
     * 停止队列监控
     */
    public String stopQueueMonitor()
    {
        queueManager.stopQueueMonitor();
        return "队列监控停止信号已发送";
    }

    /**
     * 重启队列监控
     */
    public String restartQueueMonitor()
    {
        queueManager.stopQueueMonitor();
        // 等待一下让监控线程停止
        try {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        queueManager.startQueueMonitor();
        return "队列监控已重启";
    }

    /**
     * 清空队列并重新扫描
     */
    public String resetQueue()
    {
        int clearedCount = queueManager.clearQueue();
        queueManager.scanAndEnqueueEtlTasks();
        Map<String, Object> status = queueManager.getQueueStatus();

        return String.format("队列已重置，清空了 %d 个任务，重新扫描后队列大小: %d",
                clearedCount, status.get("queueSize"));
    }

    /**
     * 更新采集任务的 json 文件表
     * 他扫描 tb_imp_etl 任务，然后生成 addax 采集需要的 json 文件模板，并写入到 tb_imp_etl_job 表中
     * 这个方法可以定期运行，确保 tb_imp_etl_job 表中的 json
     */
    public void updateJob(List<String> tids)
    {
        if (tids == null || tids.isEmpty()) {
            tids = tbImpEtlRepo.findValidTids();
        }
        for (String taskId : tids) {
            // 这里对源 DB 和 TABLE 做了 quote，用于处理不规范命名的问题，比如 mysql 中的关键字作为表名等 ，库名包含中划线(-)
            // TODO 这里直接使用 ` 来做 quote，可能不适用于所有数据库，比如 Oracle 需要使用 " 来做 quote
            // 需要根据不同数据库类型做不同的处理
            String sql = """
                    select  d.db_constr as sou_dbcon, d.db_user_etl as sou_user, d.db_pass_etl as sou_pass, t.sou_filter , t.sou_split,
                    concat('`', t.sou_owner , '`.`', t.sou_tablename, '`')  as sou_tblname,
                    'ods' || lower(t.sou_sysid) as dest_db, t.dest_tablename
                    from tb_imp_etl  t
                    join tb_imp_db d
                    on t.sou_sysid  = d.db_id_etl
                    where t.tid = '%s'
                    """.formatted(taskId);
            Map<String, Object> sourceInfo = jdbcTemplate.queryForMap(sql);
            String kind = DbUtil.getKind(sourceInfo.get("sou_dbcon").toString());
            String addaxReaderContentTemplate = jdbcTemplate.queryForObject("select entry_content from  tb_dictionary where entry_code = '5001' and entry_value = 'r" + kind + "'", String.class);
            sql = """
                    select string_agg('"'  || column_name || '"', ',' order by column_id asc) as cols from tb_imp_etl_soutab where tid = '%s'
                    """.formatted(taskId);
            String sou_col = jdbcTemplate.queryForObject(sql, String.class);
            // extra columns
            // current YYYmmdd
            sou_col += "," + "\"'${dw_clt_date}'\", \"${dw_trade_date}\"," + "\"'" + sourceInfo.get("sou_filter") + "'\"";
            sourceInfo.put("sou_col", sou_col);

            String addaxWriterTemplate = jdbcTemplate.queryForObject("select entry_content from  tb_dictionary where entry_code = '5001' and entry_value = 'wH'", String.class);
            // col_idx = 1000 的字段为分区字段，不参与 select
            sql = """
                    select string_agg('{"name": "' || col_name || '", "type": "' || col_type_full || '"}', ',' order by  col_idx asc) as tag_col
                    from tb_imp_tbl_hdp
                    where tid = '%s' and col_idx <> 1000
                    """.formatted(taskId);
            String tag_col = jdbcTemplate.queryForObject(sql, String.class);
            //TODO ods, logdate 这些应该从配置获取
            String hdfsPath = "/ods/" + sourceInfo.get("dest_db") + "/" + sourceInfo.get("dest_tablename") + "/logdate=${logdate}";
            sourceInfo.put("tag_tblname", hdfsPath);
            sourceInfo.put("tag_col", tag_col);
            addaxReaderContentTemplate = replacePlaceholders(addaxReaderContentTemplate, sourceInfo);
            addaxWriterTemplate = replacePlaceholders(addaxWriterTemplate, sourceInfo);
            String job = jdbcTemplate.queryForObject("select entry_content from  tb_dictionary where entry_code = '5000' and entry_value = '" + kind + "2H'", String.class);
            if (job == null || job.isEmpty()) {
                return;
            }
            job = job.replace("${r" + kind + "}", addaxReaderContentTemplate).replace("${wH}", addaxWriterTemplate);

            TbImpEtlJob tbImpEtlJob = new TbImpEtlJob(taskId, job);
            tbImpEtlJobRepo.save(tbImpEtlJob);
        }
    }

    private String replacePlaceholders(String template, Map<String, Object> values)
    {
        if (StringUtils.isBlank(template) || values.isEmpty()) {
            return template;
        }

        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = values.get(key);
            // 当值为 null 时，使用空字符串 '' 替代
            String replacement = value != null ? value.toString() : "";
//            String replacement = value != null ? value.toString() : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

//    /** 参数更新 */
//    public String updateParameters() {
//        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
//        if (!"1630".equals(now)) { recordSystemLog("参数更新任务不能执行,任务退出,非切日时间点"); return "非切日时间点"; }
//        sendToWecomRobot("系统参数param_sys开始切换\nTD=" + executeRedisCommand("get param.TD") + "\nCD=" + executeRedisCommand("get param.CD"));
//        List<String> tds = List.of(
//                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
//                LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")),
//                LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
//        );
//        for (String td : tds) {
//            try { executeSqlStatement("select sp_imp_param(" + td + ")"); }
//            catch (Exception e) { sendToWecomRobot("系统参数param_sys生成失败!!!!"); return "失败"; }
//        }
//        List<String> rdsCmds = querySingleColumn("select rds from vw_updt_rds");
//        for (String line : rdsCmds) log.info("更新redis: {} => {}", line, executeRedisCommand(line));
//        sendToWecomRobot("系统参数param_sys切换完成！\n TD=" + executeRedisCommand("get param.TD") + "\n CD=" + executeRedisCommand("get param.CD"));
//        recordSystemLog("参数更新任务执行完毕");
//        return "参数更新完成";
//    }

    // 特殊任务提醒
    public List<TbImpEtl> findAllSpecialTask() {
        return tbImpEtlRepo.findSpecialTasks();
    }
}
