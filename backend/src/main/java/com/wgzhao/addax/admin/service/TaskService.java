package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.dto.FillbackProgressDto;
import com.wgzhao.addax.admin.dto.FillbackResultDto;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.common.Constants;
import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.redis.MasterElectionService;
import com.wgzhao.addax.admin.redis.RedisLockService;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 采集任务管理服务类，负责采集任务队列管理及相关业务操作
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService
{
    private static final Pattern INSTANCE_WITH_PID_PATTERN = Pattern.compile("^(.*)-\\d+@.*$");
    private static final int FILLBACK_MAX_TASKS = 20;

    private final TaskQueueManager queueManager;
    private final TargetContractService targetContractService;
    private final TableService tableService;
    private final JdbcTemplate jdbcTemplate;
    private final EtlJourService jourService;
    private final SystemConfigService configService;
    private final ExecutionManager executionManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RiskLogService riskLogService;
    private final RedisLockService redisLockService;
    private final MasterElectionService electionService;
    private final EtlJobQueueService jobQueueService;
    private final TargetAdapterRegistry targetAdapterRegistry;

    /**
     * 执行指定采集源下的所有采集任务，将任务加入队列
     * 注意：近将那些表字段 start_at 为 null 的表加入队列，避免重复采集。
     *
     * @param sourceId 采集源 ID
     */
    public void executeTasksForSource(int sourceId)
    {
        List<EtlTable> tables = tableService.getRunnableInheritedTasksBySource(sourceId);
        for (EtlTable table : tables) {
            // 将采集表加入队列
            queueManager.addTaskToQueue(table);
        }
        log.info("Executing tasks for source {}, found {} tables", sourceId, tables.size());
    }

    /**
     * 处理非ETL任务（如judge任务）
     */
    public void updateParams()
    {
        // 在切日时间，开始重置所有采集任务的 flag 字段设置为 'N'，以便重新采集
        log.info("开始执行每日参数更新和任务重置...");

        // Schema refresh mutates shared state (flags, queue truncation). It may be triggered from
        // several places (master cron, manual endpoint), so gate on the master role first and then
        // take a real cluster-wide Redis lock — the previous "已获取 schema refresh 锁" comment
        // described a lock that was never written anywhere.
        if (!electionService.isMaster()) {
            log.warn("updateParams 仅允许 master 节点执行，本节点非 master，忽略本次触发");
            return;
        }
        int timeout = configService.getSchemaRefreshTimeoutSeconds();
        String lockToken = redisLockService.tryLock(Constants.SCHEMA_REFRESH_LOCK_KEY, Duration.ofSeconds(timeout + 60L));
        if (lockToken == null) {
            log.warn("schema refresh 锁被占用（{}），说明已有刷新在进行中，跳过本次刷新", Constants.SCHEMA_REFRESH_LOCK_KEY);
            return;
        }
        // Cutoff for queue cleanup below: rows created after this instant (including enqueues made
        // by other nodes while this refresh runs) must survive the truncation.
        Instant refreshStartedAt = Instant.now();
        log.info("已获取 schema refresh 锁：正在更新参数与刷新表结构，期间新任务将被延迟入队。已经在执行的任务不受影响。");

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "updateParams-worker");
            t.setDaemon(true);
            return t;
        });

        Future<?> future = null;
        try {
            // Stop the queue monitor to prevent new queued tasks from being dispatched while we refresh.
            // Note: running tasks are not interrupted.
            queueManager.stopQueueMonitor();

            // Submit refresh job and wait with timeout
            future = executor.submit(() -> {
                try {
                    // Reset flags so tasks become eligible after refresh
                    tableService.resetAllFlags();
                    // Reload system configuration
                    configService.loadConfig();

                    // Refresh schema / resources for all tables. This checks source schema and updates target metadata when changed.
                    tableService.refreshAllTableResources();

                    // Clean etl_job_queue history up to the refresh start: terminal rows and stale
                    // auto-scheduled pending rows of the previous period. Manual submits / fillbacks
                    // (pending rows carrying a payload) and rows enqueued during the refresh window
                    // are kept.
                    queueManager.truncateQueueExceptRunningTasksBefore(refreshStartedAt);
                }
                catch (Exception e) {
                    // Let outer handler deal with logging
                    throw new RuntimeException(e);
                }
            });

            try {
                future.get(timeout, TimeUnit.SECONDS);
                log.info("参数更新与表结构刷新完成");
                markSchemaRefreshDone();
            }
            catch (TimeoutException te) {
                log.error("参数更新/表结构刷新超时（>{}s），将中止刷新并释放锁", timeout);
                // try to cancel the running task
                future.cancel(true);
            }
            catch (ExecutionException ee) {
                log.error("参数更新/表结构刷新发生异常", ee.getCause());
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("updateParams 被中断");
            }
        }
        catch (Exception e) {
            log.error("更新参数或刷新表结构过程中发生错误", e);
        }
        finally {
            // Always restart the queue monitor and release the lock if we acquired it
            try {
                queueManager.startQueueMonitor();
            }
            catch (Exception e) {
                log.warn("重启队列监控器时发生错误", e);
            }

            redisLockService.release(Constants.SCHEMA_REFRESH_LOCK_KEY, lockToken);
            log.info("已释放 schema refresh 锁，队列监控器已重启，采集任务恢复正常");

            if (future != null && !future.isDone()) {
                try {
                    future.cancel(true);
                }
                catch (Exception ignored) {
                }
            }
            executor.shutdownNow();
        }
    }

    /**
     * Record that the schema refresh for the current business date completed successfully.
     * A newly elected master checks this marker after the switch time to decide whether a
     * catch-up refresh is needed (see SchemaRefreshScheduler).
     */
    private void markSchemaRefreshDone()
    {
        try {
            stringRedisTemplate.opsForValue().set(
                Constants.SCHEMA_REFRESH_DONE_KEY_PREFIX + configService.getBizDate(),
                electionService.getInstanceId(), Duration.ofDays(2));

        }
        catch (Exception e) {
            log.warn("Failed to record schema refresh completion marker", e);
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
            int result = tableService.findPendingTasks();
            if (result > 0) {
                detailedStatus.put("pendingInDatabase", result);
            }
            result = tableService.findRunningTasks();
            if (result > 0) {
                detailedStatus.put("runningInDatabase", result);
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
     * 启动队列监控
     */
    public String startQueueMonitor()
    {
        queueManager.startQueueMonitor();
        return "队列监控已启动";
    }

    /**
     * 清空队列并重新扫描
     */
    public String resetQueue()
    {
        int clearedCount = queueManager.clearQueue();
        queueManager.scanAndEnqueueEtlTasks();
        Map<String, Object> status = queueManager.getQueueStatus();

        return String.format("队列已重置，清空了 %d 个任务，重新扫描后队列大小: %s",
            clearedCount, status.get("queueSize"));
    }

    // 特殊任务提醒
    public List<EtlTable> findAllSpecialTask()
    {
        return tableService.findSpecialTasks();
    }

    public TaskResultDto submitTask(long tableId)
    {
        TaskResultDto contractFailure = checkExternalContract(tableId);
        if (contractFailure != null)
        {
            return contractFailure;
        }
        if (queueManager.isRefreshing()) {
            log.info("当前正在更新参数/刷新表结构，拒绝直接提交任务：tableId={}", tableId);
            return TaskResultDto.failure("正在刷新表结构，暂时无法提交任务", 0);
        }
        if (queueManager.addTaskToQueue(tableId)) {
            return TaskResultDto.success("任务已提交到队列", 0);
        }
        else {
            return TaskResultDto.failure("任务提交失败，可能是队列已满或任务已存在", 0);
        }
    }

    public TaskResultDto submitTask(long tableId, String username)
    {
        TaskResultDto contractFailure = checkExternalContract(tableId);
        if (contractFailure != null)
        {
            return contractFailure;
        }
        if (queueManager.isRefreshing()) {
            log.info("当前正在更新参数/刷新表结构，拒绝直接提交任务：tableId={}", tableId);
            return TaskResultDto.failure("正在刷新表结构，暂时无法提交任务", 0);
        }

        String payload = null;
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("submitter", username);
            map.put("action", "collect");
            payload = objectMapper.writeValueAsString(map);
        }
        catch (Exception e) {
            log.debug("Failed to serialize task payload for tid={}", tableId);
        }

        if (queueManager.addTaskToQueue(tableId, payload)) {
            return TaskResultDto.success("任务已提交到队列", 0);
        }
        else {
            return TaskResultDto.failure("任务提交失败，可能是队列已满或任务已存在", 0);
        }
    }

    private TaskResultDto checkExternalContract(long tableId)
    {
        VwEtlTableWithSource table = tableService.getTableView(tableId);
        if (table == null || table.getSchemaManagementMode() != SchemaManagementMode.EXTERNAL)
        {
            return null;
        }
        TargetContractReport report = targetContractService.check(table);
        if (!report.blocked())
        {
            return null;
        }
        return TaskResultDto.failure("目标表契约校验失败，任务未提交: " + report.summary(), 0);
    }

    /**
     * Submit fillback (补数) tasks for the given tables and dates.
     * Each (table, date) pair is enqueued as a separate task with the fillback date
     * as its biz_date. Tables without a date dimension (no Hive partition column and no
     * RDBMS business date column) are skipped with a risk log entry.
     * Total task count (tables × dates) must not exceed {@value #FILLBACK_MAX_TASKS}.
     *
     * <p>{@code dates} is the detail page's calendar selection and takes precedence over the
     * start/end range; the range form is kept for the list page and older clients.
     *
     * @param tids      table IDs to fillback
     * @param startDate fillback start date (inclusive), used when {@code dates} is absent
     * @param endDate   fillback end date (inclusive), used when {@code dates} is absent
     * @param dates     explicit target dates
     * @param username  requesting user
     * @return fillback result with batch id and enqueue details
     */
    public FillbackResultDto fillback(List<Long> tids, LocalDate startDate, LocalDate endDate,
        List<LocalDate> dates, String username)
    {
        if (tids == null || tids.isEmpty()) {
            throw new ApiException(400, "补数表列表不能为空");
        }
        List<LocalDate> targetDates = resolveFillbackDates(startDate, endDate, dates);
        if (targetDates.isEmpty()) {
            throw new ApiException(400, "补数日期不能为空");
        }

        // Fillback is for history: today's period belongs to the normal scheduler, and dates
        // beyond the current business date have no data to re-collect.
        LocalDate maxDate = configService.getBizDateAsDate();
        for (LocalDate date : targetDates) {
            if (date.isAfter(maxDate)) {
                throw new ApiException(400,
                    "补数日期 " + date + " 晚于当前业务日期 " + maxDate);
            }
        }

        long totalTasks = (long) tids.size() * targetDates.size();
        if (totalTasks > FILLBACK_MAX_TASKS) {
            throw new ApiException(400,
                String.format("补数任务总数(%d)超过上限(%d)：表数=%d, 天数=%d",
                    totalTasks, FILLBACK_MAX_TASKS, tids.size(), targetDates.size()));
        }

        if (queueManager.isRefreshing()) {
            throw new ApiException(409, "正在刷新表结构，暂时无法提交补数任务");
        }

        String batchId = UUID.randomUUID().toString();
        Instant submittedAt = Instant.now();
        LocalDate rangeStart = targetDates.getFirst();
        LocalDate rangeEnd = targetDates.getLast();
        DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;

        int totalEnqueued = 0;
        int skipped = 0;
        List<FillbackResultDto.Detail> details = new ArrayList<>();

        for (Long tid : tids) {
            EtlTable table = tableService.getTable(tid);
            if (table == null) {
                details.add(new FillbackResultDto.Detail(tid, "(unknown)", List.of(), 0, true, "表不存在"));
                continue;
            }
            if (table.getRetiredAt() != null) {
                details.add(new FillbackResultDto.Detail(tid, "(retired)", List.of(), 0, true, "采集表已退役"));
                continue;
            }

            VwEtlTableWithSource tableView = tableService.getTableView(tid);
            if (tableView == null) {
                details.add(new FillbackResultDto.Detail(tid, "(unknown)", List.of(), 0, true, "表视图不存在"));
                continue;
            }

            String tableLabel = tableView.getTargetDb() + "." + tableView.getTargetTable();

            // Tables without a date dimension cannot be fillbacked: multiple dates would overwrite each
            // other. Ask the target adapter instead of reading Hive columns directly, so an RDBMS target
            // is judged by its own business date column rather than by a leftover partition attribute.
            if (!targetAdapterRegistry.resolve(tableView).hasDateDimension(tableView)) {
                String reason = "该表没有可用的日期维度（HDFS 分区字段或 RDBMS 快照业务日期列），不支持补数";
                riskLogService.recordRisk("Fillback", "WARN",
                    tableLabel + ": " + reason, tid);
                details.add(new FillbackResultDto.Detail(tid, tableLabel, List.of(), 0, true, reason));
                continue;
            }

            List<String> enqueuedDates = new ArrayList<>();
            int enqueuedForTable = 0;
            for (LocalDate date : targetDates) {
                String payload = buildFillbackPayload(username, date, batchId, targetDates, submittedAt);
                if (queueManager.addFillbackTaskToQueue(table, date, payload)) {
                    enqueuedForTable++;
                    totalEnqueued++;
                    enqueuedDates.add(date.format(dateFmt));
                } else {
                    skipped++;
                }
            }

            // Record audit journey
            EtlJour jour = jourService.addJour(tid, JourKind.FILLBACK,
                String.format("fillback batch=%s %s ~ %s dates=%d by %s, enqueued=%d",
                    batchId, rangeStart.format(dateFmt), rangeEnd.format(dateFmt),
                    targetDates.size(), username, enqueuedForTable));
            jourService.successJour(jour);

            details.add(new FillbackResultDto.Detail(tid, tableLabel, enqueuedDates, enqueuedForTable, false, null));
        }

        log.info("Fillback completed: batch={}, totalEnqueued={}, skipped={}, tables={}, dates={}",
            batchId, totalEnqueued, skipped, tids.size(), targetDates.size());
        return new FillbackResultDto(batchId, submittedAt, totalEnqueued, skipped, details);
    }

    /**
     * Resolve the requested dates, keeping the explicit calendar selection as the source of truth.
     */
    private List<LocalDate> resolveFillbackDates(LocalDate startDate, LocalDate endDate, List<LocalDate> dates)
    {
        if (dates != null && !dates.isEmpty()) {
            return dates.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        }
        if (startDate == null || endDate == null) {
            throw new ApiException(400, "补数日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new ApiException(400, "开始日期不能晚于结束日期");
        }
        return startDate.datesUntil(endDate.plusDays(1)).toList();
    }

    private String buildFillbackPayload(String username, LocalDate date, String batchId,
        List<LocalDate> requestedDates, Instant submittedAt)
    {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("submitter", username);
            map.put("action", "fillback");
            map.put("fillback_date", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            map.put("batch_id", batchId);
            map.put("submitted_at", submittedAt.toString());
            map.put("requested_dates", requestedDates.stream().map(LocalDate::toString).toList());
            return objectMapper.writeValueAsString(map);
        }
        catch (Exception e) {
            // A null payload would make the row invisible to progress queries, so fail the whole
            // submission instead of enqueueing work that cannot be tracked.
            throw new IllegalStateException("Failed to serialize fillback payload", e);
        }
    }

    /**
     * Progress of one fillback batch. The batch metadata lives in the queue row payload; dates
     * without a row are reported as unresolved (deduplicated at submit time or cleaned up).
     *
     * @param tid     table id
     * @param batchId batch id, null for the table's latest batch
     * @return progress snapshot
     */
    public FillbackProgressDto getFillbackProgress(long tid, String batchId)
    {
        LocalDate maxDate = configService.getBizDateAsDate();
        VwEtlTableWithSource view = tableService.getTableView(tid);
        boolean supported = false;
        String reason = null;
        boolean retired = false;
        boolean sourceEnabled = true;
        String tableStatus = null;
        String tableLabel = "(unknown)";

        if (view == null) {
            reason = "采集表不存在";
        }
        else {
            tableLabel = view.getTargetDb() + "." + view.getTargetTable();
            retired = view.getRetiredAt() != null;
            sourceEnabled = !Boolean.FALSE.equals(view.getEnabled());
            tableStatus = view.getStatus();
            if (retired) {
                reason = "采集表已退役";
            }
            else {
                try {
                    supported = targetAdapterRegistry.resolve(view).hasDateDimension(view);
                    if (!supported) {
                        reason = "该表没有可用的日期维度（HDFS 分区字段或 RDBMS 快照业务日期列），不支持补数";
                    }
                }
                catch (Exception e) {
                    reason = "目标端配置无效: " + e.getMessage();
                }
            }
        }

        String effectiveBatch = batchId == null || batchId.isBlank() ? null : batchId.trim();
        if (effectiveBatch != null && !isUuid(effectiveBatch)) {
            throw new ApiException(400, "batchId 格式不正确");
        }
        if (effectiveBatch == null) {
            effectiveBatch = jobQueueService.findLatestFillbackBatchId(tid).orElse(null);
        }
        if (effectiveBatch == null) {
            return emptyProgress(tid, tableLabel, supported, reason, retired, sourceEnabled, tableStatus, maxDate, null);
        }

        List<EtlJobQueueService.FillbackJobRow> rows = jobQueueService.findFillbackBatchRows(tid, effectiveBatch);
        if (rows.isEmpty()) {
            // The batch may be older than the daily queue cleanup, or every date was deduplicated
            // at submit time (then the row payload, and with it the requested dates, never existed).
            return emptyProgress(tid, tableLabel, supported, reason, retired, sourceEnabled, tableStatus, maxDate, effectiveBatch);
        }

        String firstPayload = rows.getFirst().payload();
        List<LocalDate> requestedDates = parseRequestedDates(firstPayload);
        String submitter = parseText(firstPayload, "submitter");
        Instant submittedAt = parseInstant(firstPayload, "submitted_at");
        if (submittedAt == null) {
            submittedAt = rows.stream()
                .map(EtlJobQueueService.FillbackJobRow::createdAt)
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        }

        // A batch has one row per date; keep the newest row if a manual DB edit left duplicates.
        Map<LocalDate, EtlJobQueueService.FillbackJobRow> byDate = new HashMap<>();
        for (EtlJobQueueService.FillbackJobRow row : rows) {
            if (row.bizDate() == null) {
                continue;
            }
            EtlJobQueueService.FillbackJobRow previous = byDate.get(row.bizDate());
            if (previous == null || row.id() > previous.id()) {
                byDate.put(row.bizDate(), row);
            }
        }
        if (requestedDates.isEmpty()) {
            requestedDates = byDate.keySet().stream().sorted().toList();
        }

        int pending = 0;
        int running = 0;
        int completed = 0;
        int failed = 0;
        int cancelled = 0;
        int unresolved = 0;
        List<FillbackProgressDto.Item> items = new ArrayList<>();

        for (LocalDate date : requestedDates) {
            EtlJobQueueService.FillbackJobRow row = byDate.get(date);
            if (row == null) {
                unresolved++;
                items.add(new FillbackProgressDto.Item(date, "unresolved", 0, 0, null, null, null, null, null));
                continue;
            }
            switch (String.valueOf(row.status())) {
                case "pending" -> pending++;
                case "running" -> running++;
                case "completed" -> completed++;
                case "failed" -> failed++;
                case "cancelled" -> cancelled++;
                default -> unresolved++;
            }
            String error = row.execError() != null ? row.execError() : row.lastError();
            items.add(new FillbackProgressDto.Item(date, row.status(), row.attempts(), row.maxAttempts(),
                error, row.id(), row.createdAt(), row.execStartedAt(), row.execFinishedAt()));
        }

        FillbackProgressDto.Counts counts = new FillbackProgressDto.Counts(
            requestedDates.size(), pending, running, completed, failed, cancelled, unresolved);
        String state = computeFillbackState(counts);
        return new FillbackProgressDto(true, effectiveBatch, tid, tableLabel, supported, reason, retired,
            sourceEnabled, tableStatus, maxDate,
            requestedDates.isEmpty() ? null : requestedDates.getFirst(),
            requestedDates.isEmpty() ? null : requestedDates.getLast(),
            submitter, submittedAt, requestedDates.size(), state, counts, items);
    }

    private FillbackProgressDto emptyProgress(long tid, String table, boolean supported, String reason,
        boolean retired, boolean sourceEnabled, String tableStatus, LocalDate maxDate, String batchId)
    {
        return new FillbackProgressDto(false, batchId, tid, table, supported, reason, retired, sourceEnabled,
            tableStatus, maxDate, null, null, null, null, 0, "UNKNOWN",
            new FillbackProgressDto.Counts(0, 0, 0, 0, 0, 0, 0), List.of());
    }

    private static String computeFillbackState(FillbackProgressDto.Counts counts)
    {
        if (counts.total() == 0) {
            return "UNKNOWN";
        }
        if (counts.running() > 0) {
            return "RUNNING";
        }
        if (counts.pending() > 0) {
            return "PENDING";
        }
        int finished = counts.completed() + counts.failed() + counts.cancelled();
        if (finished == 0) {
            return "UNKNOWN";
        }
        if (counts.failed() == 0 && counts.cancelled() == 0 && counts.unresolved() == 0
            && counts.completed() == counts.total()) {
            return "COMPLETED";
        }
        if (counts.completed() > 0) {
            return "PARTIAL_SUCCESS";
        }
        if (counts.failed() > 0) {
            return "FAILED";
        }
        if (counts.cancelled() > 0) {
            return "CANCELLED";
        }
        return "UNKNOWN";
    }

    private List<LocalDate> parseRequestedDates(String payload)
    {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(payload).path("requested_dates");
            if (!node.isArray()) {
                return List.of();
            }
            List<LocalDate> dates = new ArrayList<>();
            node.forEach(item -> {
                try {
                    dates.add(LocalDate.parse(item.asText()));
                }
                catch (RuntimeException ignored) {
                    // a malformed entry is skipped; the other dates still describe the batch
                }
            });
            return dates.stream().distinct().sorted().toList();
        }
        catch (Exception e) {
            log.warn("Failed to parse fillback payload: {}", e.getMessage());
            return List.of();
        }
    }

    private String parseText(String payload, String field)
    {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload).path(field);
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        }
        catch (Exception e) {
            return null;
        }
    }

    private Instant parseInstant(String payload, String field)
    {
        String value = parseText(payload, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isUuid(String value)
    {
        try {
            UUID.fromString(value);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Kill a running task by table id. If running on this instance, kill locally; otherwise publish a Redis kill message.
     */
    public TaskResultDto killTask(long tid)
    {
        try {
            // Decide what there is to kill from the queue state first. A kill only reaches a process
            // once it is registered in ExecutionManager: queued-but-not-dispatched rows would
            // otherwise be published a 'successful' kill while running moments later.
            List<EtlJobQueue> activeRows = jobQueueService.findActiveByTid(tid);
            boolean hasRunning = activeRows.stream().anyMatch(row -> "running".equals(row.getStatus()));
            int pendingCancelled = jobQueueService.cancelPendingByTid(tid);

            if (!hasRunning) {
                if (pendingCancelled > 0) {
                    log.warn("Cancelled {} queued job(s) for table {} by user request", pendingCancelled, tid);
                    recordKillJour(tid, "Cancelled by user request");
                    return TaskResultDto.success("Cancelled " + pendingCancelled + " queued job(s)", 0);
                }
                log.warn("No running or queued task found for table {} on kill request", tid);
                return TaskResultDto.failure("No running or queued task found for tid=" + tid, 0);
            }

            // try local kill first
            boolean killedLocal = executionManager.killLocal(tid);
            if (killedLocal) {
                log.warn("Killed local collecting table {} by request", tid);
                recordKillJour(tid, "Killed by user request");
                return TaskResultDto.success("Killed local collecting job", 0);
            }

            // publish kill to redis channel for remote nodes to handle
            String channel = "etl:kill";
            String payload = String.valueOf(tid);
            try {
                stringRedisTemplate.convertAndSend(channel, payload);
                // set fallback signal key for a short period so a node that receives the assignment
                // after the pub/sub kill message was missed still honors the kill before starting
                stringRedisTemplate.opsForValue().set(
                    Constants.TASK_KILL_SIGNAL_KEY_PREFIX + tid, "1", java.time.Duration.ofSeconds(30));
                log.info("Published kill request for job {} to channel {}", tid, channel);
                recordKillJour(tid, "Kill requested by user (remote)");
                return TaskResultDto.success("Kill request published", 0);
            }
            catch (Exception e) {
                log.error("Failed to publish kill request for job {}", tid, e);
                return TaskResultDto.failure("Failed to publish kill request: " + e.getMessage(), 0);
            }
        }
        catch (Exception e) {
            log.error("killTask failed for job {}", tid, e);
            return TaskResultDto.failure(e.getMessage() == null ? "internal error" : e.getMessage(), 0);
        }
    }

    private void recordKillJour(long tid, String reason)
    {
        try {
            EtlJour etlJour = jourService.getLastByTidWithKind(tid, null);
            jourService.failJour(etlJour, reason);
        }
        catch (Exception e) {
            log.warn("Failed to record kill reason to etl_jour for table {}", tid, e);
        }
    }

    public List<Map<String, Object>> getAllTaskStatus()
    {
        // 第一次采集时的估算默认耗时（秒），可按需调整或从配置读取
        int defaultTakeSecs = 300;
        String sql = """
            select
            id,
            target_db || '.' ||  target_table as tbl,
            status,
            to_char(start_time, 'yyyy-MM-dd HH24:MM:SS') as start_time,
            q.claimed_by,
            least(
            100,
            round(
            case when status in ('E','W') then 0
                else  extract(epoch from now() - t.start_time) * 100.0 / COALESCE(b.take_secs, ?)
            end ,0
            )
            )::int
            as progress
            from etl_table t
            left join
            (
            select tid,
            take_secs,
            row_number() over (partition by tid order by start_at desc) as rn
            from etl_statistic
            ) b
            on t.id = b.tid and t.status in ( 'R', 'W')
            left join
            (
            select tid,
            claimed_by,
            row_number() over (partition by tid order by claimed_at desc, id desc) as qrn
            from etl_job_queue
            where status = 'running'
            ) q
            on t.id = q.tid and q.qrn = 1
            where t.status in ('R', 'W')
              and (rn = 1 or rn is null)
            order by id
            """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, defaultTakeSecs);
        for (Map<String, Object> row : rows) {
            Object idObj = row.get("id");
            if (!(idObj instanceof Number num)) {
                row.put("node_name", "");
                continue;
            }
            long tid = num.longValue();
            String instanceId = row.get("claimed_by") == null ? "" : String.valueOf(row.get("claimed_by"));
            if (instanceId.isBlank()) {
                instanceId = executionManager.findInstanceId(tid).orElse("");
            }
            row.put("node_name", extractNodeName(instanceId));
            row.remove("claimed_by");
        }
        return rows;
    }

    private String extractNodeName(String instanceId)
    {
        if (instanceId == null || instanceId.isBlank()) {
            return "";
        }
        Matcher matcher = INSTANCE_WITH_PID_PATTERN.matcher(instanceId);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        int idx = instanceId.lastIndexOf('-');
        return idx > 0 ? instanceId.substring(0, idx) : instanceId;
    }
}
