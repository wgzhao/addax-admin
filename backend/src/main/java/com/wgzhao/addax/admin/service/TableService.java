package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.FailureStage;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.common.SchemaManagementMode;
import com.wgzhao.addax.admin.dto.TargetContractReport;
import com.wgzhao.addax.admin.dto.BatchTableStatusDto;
import com.wgzhao.addax.admin.dto.TableListCriteria;
import com.wgzhao.addax.admin.dto.TableRefreshOutcome;
import com.wgzhao.addax.admin.dto.TableRefreshStats;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.EtlJobQueue;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.service.collection.CollectionScopeService;
import jakarta.persistence.EntityManager;
import com.wgzhao.addax.admin.service.target.TargetAdapterRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 采集表信息服务类，负责采集表的增删改查及资源刷新等业务操作
 */
@Service
@Slf4j
@AllArgsConstructor
public class TableService
{
    private final EtlTableRepo etlTableRepo;
    private final ColumnService columnService;
    private final JobContentService jobContentService;
    private final DictService dictService;
    private final EtlJourService jourService;
    private final VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;
    private final TargetAdapterRegistry targetAdapterRegistry;
    private final SystemConfigService configService;
    private final UserNotificationService userNotificationService;
    private final EntityManager entityManager;
    private final EtlJobQueueService jobQueueService;
    private final CalendarService calendarService;
    private final TargetContractService targetContractService;
    private final CollectionScopeService collectionScopeService;
    private final FailureRecorder failureRecorder;

    /**
     * Columns the collection-table list may sort by. Sorting straight from a request parameter let
     * any entity property through, including ones the list never exposes.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
        "id", "name", "sourceTable", "targetTable", "filter", "status", "duration",
        "endTime", "startTime", "createdAt", "updatedAt");

    /**
     * Target databases this source most recently used on the given endpoint. Used by the batch
     * wizard as a convenience default; callers must still treat the target database as user input.
     */
    public List<String> findRecentTargetDbs(int sid, long targetId)
    {
        return etlTableRepo.findRecentTargetDbs(sid, targetId);
    }

    /**
     * 刷新指定采集表的资源（如字段、模板等）
     *
     * @param table 采集表对象
     * @return 任务结果
     */
    public TaskResultDto refreshTableResources(EtlTable table)
    {
        return refreshTableResourcesDetailed(table).result();
    }

    /**
     * Same refresh, but keeps the failure kind so a whole-round summary can count
     * source outages separately from schema and job-template failures.
     */
    private TableRefreshOutcome refreshTableResourcesDetailed(EtlTable table)
    {
        boolean columnsUpdated = false;
        if (table == null) {
            return fail(TableRefreshOutcome.FailureKind.INVALID_TABLE, "Table is null");
        }
        // Check for thread interruption early to allow cooperative cancellation
        if (Thread.currentThread().isInterrupted()) {
            log.info("refreshTableResources interrupted before starting for table {}", table.getId());
            return fail(TableRefreshOutcome.FailureKind.INTERRUPTED, "Refresh interrupted");
        }
        VwEtlTableWithSource vwTable = vwEtlTableWithSourceRepo.findById(table.getId()).orElse(null);
        if (vwTable == null) {
            log.warn("Table view not found for tid {}", table.getId());
            return fail(TableRefreshOutcome.FailureKind.VIEW_MISSING,
                "Table view not found for tid " + table.getId());
        }

        String sourceTable = vwTable.getSourceTable();
        if (sourceTable.contains("${")) {
            // 动态表名，进行替换
            log.info("Source table name {} contains dynamic variable, replace placeholder", sourceTable);
            StringSubstitutor substitutor = new StringSubstitutor(configService.getBizDateValues());
            sourceTable = substitutor.replace(sourceTable);
            vwTable.setSourceTable(sourceTable);
        }

        // 1. 更新列信息
        // cooperative check before long-running column update
        if (Thread.currentThread().isInterrupted()) {
            log.info("refreshTableResources interrupted before updating columns for table {}", table.getId());
            return fail(TableRefreshOutcome.FailureKind.INTERRUPTED, "Refresh interrupted");
        }
        int retCode = columnService.updateTableColumnsV2(vwTable);

        if (retCode == ColumnService.UPDATE_SOURCE_UNAVAILABLE) {
            // A transient source outage must not mark the table as a collection failure: the previous
            // job template can still run, and the next refresh will try the source again.
            log.warn("Source database unavailable for table {}, skip this refresh round", table.getId());
            return fail(TableRefreshOutcome.FailureKind.SOURCE_UNAVAILABLE,
                "Source database unavailable for table id " + table.getId());
        }

        if (retCode == ColumnService.UPDATE_FAILED) {
            setFailed(table.getId(), durationOf(table));
            log.warn("Failed to update columns for table id {}", table.getId());
            return fail(TableRefreshOutcome.FailureKind.COLUMNS_FAILED,
                "Failed to update columns for table id " + table.getId());
        }

        columnsUpdated = retCode != ColumnService.UPDATE_NO_CHANGE;
        boolean targetSchemaChanged = columnsUpdated
            || Objects.equals(vwTable.getStatus(), TableStatus.WAIT_SCHEMA);
        SchemaManagementMode schemaMode = table.getSchemaManagementMode() == null
            ? SchemaManagementMode.MANAGED : table.getSchemaManagementMode();
        if (schemaMode == SchemaManagementMode.EXTERNAL)
        {
            TargetContractReport report = targetContractService.check(vwTable);
            if (report.blocked())
            {
                setUpdateSchema(table.getId());
                failureRecorder.record(table.getId(), "目标表契约校验失败: " + report.summary(),
                    FailureEvidence.create()
                        .stage(FailureStage.TARGET_SCHEMA)
                        .errorClass("TargetContractViolation")
                        .detail(report.summary())
                        .bizDate(configService.getBizDateAsDate()));
                return fail(TableRefreshOutcome.FailureKind.TARGET_CONTRACT_BLOCKED,
                    "目标表契约校验失败: " + report.summary());
            }
            table.setTargetSchemaFingerprint(report.fingerprint());
            etlTableRepo.save(table);
        }
        else if (targetSchemaChanged)
        {
            if (Thread.currentThread().isInterrupted()) {
                log.info("refreshTableResources interrupted before creating/updating target table for {}", table.getId());
                return fail(TableRefreshOutcome.FailureKind.INTERRUPTED, "Refresh interrupted");
            }
            if (!targetAdapterRegistry.resolve(vwTable).createOrUpdateTable(vwTable)) {
                setUpdateSchema(table.getId());
                log.warn("Failed to create or update target table for tid {}", table.getId());
                return fail(TableRefreshOutcome.FailureKind.TARGET_DDL_FAILED,
                    "Failed to create or update target table for tid " + table.getId());
            }
        }
        if (!columnsUpdated && !targetSchemaChanged)
        {
            setNotCollectIfDue(table);
        }

        // 2. 更新任务文件
        if (Thread.currentThread().isInterrupted()) {
            log.info("refreshTableResources interrupted before updating job content for {}", table.getId());
            return fail(TableRefreshOutcome.FailureKind.INTERRUPTED, "Refresh interrupted");
        }
        TaskResultDto result = jobContentService.updateJob(vwTable);
        if (result.success()) {
            setNotCollectIfDue(table);
            return new TableRefreshOutcome(
                TaskResultDto.success("Table resources refreshed successfully ", 0),
                TableRefreshOutcome.FailureKind.NONE);
        }
        else {
            setFailed(table.getId(), durationOf(table));
            log.warn("Failed to update job content for tid {}", table.getId());
            return fail(TableRefreshOutcome.FailureKind.JOB_CONTENT_FAILED,
                "Failed to update job content for tid " + table.getId());
        }
    }

    private static TableRefreshOutcome fail(TableRefreshOutcome.FailureKind kind, String message)
    {
        return new TableRefreshOutcome(TaskResultDto.failure(message, 0), kind);
    }

    /**
     * 刷新指定ID的采集表资源
     *
     * @param tableId 采集表ID
     * @return 任务结果
     */
    public TaskResultDto refreshTableResources(long tableId)
    {
        EtlTable table = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new IllegalArgumentException("Table not found with id: " + tableId));
        return refreshTableResources(table);
    }

    /**
     * 异步刷新采集表资源
     *
     * @param table 采集表对象
     */
    @Async
    public void refreshTableResourcesAsync(EtlTable table)
    {
        refreshTableResources(table);
    }

    /**
     * 刷新标记为 U 的表资源
     * /
     */
    private void refreshUpdatedTableResourcesInternal()
    {
        List<EtlTable> tables = etlTableRepo.findByStatusAndRetiredAtIsNull("U");
        for (EtlTable table : tables) {
            // cooperative cancellation: if current thread interrupted or refresh flag cleared, stop
            if (Thread.currentThread().isInterrupted()) {
                log.info("refreshUpdatedTableResources interrupted, stopping at table {}", table.getId());
                break;
            }
            try {
                refreshTableResourcesAsync(table);
            }
            catch (Exception e) {
                log.error("Failed to refresh resources for table {}", table.getId(), e);
            }
        }
    }

    @Async
    public void refreshUpdatedTableResources()
    {
        refreshUpdatedTableResourcesInternal();
    }

    @Async
    public void refreshUpdatedTableResourcesAsyncWithNotify(String username)
    {
        refreshUpdatedTableResourcesInternal();
        notifyUser(username, "表信息更新完成", "已完成标记为 U 的表资源刷新。", "TABLE_REFRESH");
    }

    /**
     * 刷新所有采集表的资源
     *
     * <p>Returns the round's counts instead of swallowing per-table failures: a
     * partial refresh is otherwise invisible outside the log, and the day-cut
     * event has to report how many tables were skipped and why.</p>
     */
    public TableRefreshStats refreshAllTableResources()
    {
        List<EtlTable> tables = etlTableRepo.findCanRefreshTables();
        int success = 0;
        int failed = 0;
        int sourceUnavailable = 0;
        int waitSchemaToNotCollect = 0;
        List<TableRefreshStats.Failure> failures = new ArrayList<>();
        for (EtlTable table : tables) {
            // cooperative cancellation: if current thread interrupted or refresh flag cleared, stop
            if (Thread.currentThread().isInterrupted()) {
                log.info("refreshAllTableResources interrupted, stopping at table {}", table.getId());
                break;
            }
            boolean wasWaitSchema = TableStatus.WAIT_SCHEMA.equals(table.getStatus());
            try {
                TableRefreshOutcome outcome = refreshTableResourcesDetailed(table);
                if (outcome.failure() == TableRefreshOutcome.FailureKind.SOURCE_UNAVAILABLE) {
                    sourceUnavailable++;
                }
                if (!outcome.result().success()) {
                    failed++;
                    failures.add(new TableRefreshStats.Failure(table.getId(), table.getSourceDb(),
                        table.getSourceTable(), outcome.result().message()));
                    continue;
                }
                success++;
                // A 'U' row leaves the "waiting for schema" state only when the business
                // period actually owes it a collection; on a rest day it legitimately stays.
                if (wasWaitSchema && TableStatus.NOT_COLLECT.equals(table.getStatus())) {
                    waitSchemaToNotCollect++;
                }
            }
            catch (Exception e) {
                failed++;
                String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                failures.add(new TableRefreshStats.Failure(table.getId(), table.getSourceDb(),
                    table.getSourceTable(), reason));
                log.error("Failed to refresh resources for table {}", table.getId(), e);
            }
        }
        TableRefreshStats stats = new TableRefreshStats(tables.size(), success, failed,
            sourceUnavailable, waitSchemaToNotCollect, failures);
        log.info("Table resource refresh finished: total={}, success={}, failed={}, sourceUnavailable={}, uToN={}",
            stats.total(), stats.success(), stats.failed(), stats.sourceUnavailable(),
            stats.waitSchemaToNotCollect());
        return stats;
    }

    @Async
    public void refreshAllTableResourcesAsyncWithNotify(String username)
    {
        TableRefreshStats stats = refreshAllTableResources();
        notifyUser(username, "表信息更新完成",
            String.format("已完成全部采集表资源刷新：成功 %d 个，失败 %d 个。", stats.success(), stats.failed()),
            "TABLE_REFRESH");
    }

    @Async
    public void refreshTablesResourcesAsync(List<EtlTable> tables, String username)
    {
        int success = 0;
        int failed = 0;
        for (EtlTable table : tables) {
            TaskResultDto result = refreshTableResources(table);
            if (result.success()) {
                success++;
            }
            else {
                failed++;
            }
        }
        String content = String.format("批量刷新完成：成功 %d 个，失败 %d 个。", success, failed);
        notifyUser(username, "批量表资源刷新完成", content, "TABLE_REFRESH");
    }

    private void notifyUser(String username, String title, String content, String type)
    {
        if (username == null || username.isBlank()) {
            return;
        }
        userNotificationService.create(username, title, content, type, null, null);
    }

    /**
     * ODS 采集信息
     *
     */
    public Page<VwEtlTableWithSource> getVwTablesInfo(int page, int pageSize, String q, Integer sourceId, String sortField, String sortOrder)
    {
        return findTables(page, pageSize, q, List.of(), sourceId, sortField, sortOrder, "CURRENT");
    }

    /**
     * 根据状态列表获取视图表信息
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @param q 查询关键字
     * @param statuses 表状态列表
     * @param sortField 排序字段
     * @param sortOrder 排序方式
     * @return 视图表信息分页结果
     */
    public Page<VwEtlTableWithSource> getVwTablesByStatuses(int page, int pageSize, String q, List<String> statuses, Integer sourceId,
        String sortField, String sortOrder)
    {
        return findTables(page, pageSize, q, statuses, sourceId, sortField, sortOrder, "CURRENT");
    }

    public Page<VwEtlTableWithSource> findTables(int page, int pageSize, String q, List<String> statuses,
        Integer sourceId, String sortField, String sortOrder, String scope)
    {
        return findTables(page, pageSize, q, statuses, sourceId, sortField, sortOrder, scope, null);
    }

    /**
     * @param allowedSourceIds null means every source (admin/guest); a set limits the query to that
     *                         set. The filter is applied in SQL so pagination totals stay correct.
     */
    public Page<VwEtlTableWithSource> findTables(int page, int pageSize, String q, List<String> statuses,
        Integer sourceId, String sortField, String sortOrder, String scope,
        Collection<Integer> allowedSourceIds)
    {
        TableListCriteria criteria = new TableListCriteria(
            page, pageSize, q, statuses == null ? List.of() : statuses, scope,
            sourceId == null ? List.of() : List.of(sourceId),
            List.of(), null, List.of(), List.of(), null, null,
            List.of(), List.of(), null, null, null,
            null, null, null, null, null, null,
            sortField, sortOrder);
        return findTables(criteria, allowedSourceIds);
    }

    /**
     * Filtered list of collection tables. Every dimension is optional; unset values are ignored, so
     * conditions compose instead of overriding each other.
     */
    public Page<VwEtlTableWithSource> findTables(TableListCriteria criteria, Collection<Integer> allowedSourceIds)
    {
        Pageable pageable = PageRequest.of(criteria.page(), criteria.pageSize(),
            generateTableSort(criteria.sortField(), criteria.sortOrder()));
        if (allowedSourceIds != null && allowedSourceIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return vwEtlTableWithSourceRepo.findAll(
            TableSpecifications.byCriteria(criteria, allowedSourceIds), pageable);
    }

    /**
     * Sorts the list by an allowlisted column and always appends {@code id} as the tiebreaker, so a
     * page does not silently repeat or skip rows when the chosen column has duplicates.
     */
    static Sort generateTableSort(String sortField, String sortOrder)
    {
        if (sortField == null || !SORTABLE_FIELDS.contains(sortField)) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder)
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        if ("id".equals(sortField)) {
            return Sort.by(direction, "id");
        }
        return Sort.by(direction, sortField).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    /**
     * 获取单个表的详细信息
     *
     * @param tid 表ID
     * @return 视图表对象
     */
    public VwEtlTableWithSource findOneTableInfo(long tid)
    {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null);
    }

    // 找到所有需要采集的表

    /**
     * 统计所有待采集任务数量
     *
     * @return 待采集任务数量
     */
    public int findPendingTasks()
    {
        return etlTableRepo.countByStatusEquals(TableStatus.NOT_COLLECT);
    }

    /**
     * 统计所有正在运行的任务数量
     *
     * @return 正在运行的任务数量
     */
    public int findRunningTasks()
    {
        return etlTableRepo.countByStatusEquals(TableStatus.COLLECTING);
    }

    /**
     * 根据ID获取表信息
     *
     * @param tid 表ID
     * @return 表对象
     */
    public EtlTable getTable(long tid)
    {
        return etlTableRepo.findById(tid).orElse(null);
    }

    /**
     * 根据ID获取视图表信息
     *
     * @param tid 表ID
     * @return 视图表对象
     */
    public VwEtlTableWithSource getTableView(long tid)
    {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null);
    }

    /**
     * 设置任务为正在运行状态。
     *
     * <p>用条件更新而不是整行 save：调度线程持有的实体可能已过期，整行回写会覆盖并发修改；
     * 并且停采（X）表允许手工执行/补数，这里不能把 X 改成 R，否则结束时会写成 Y，
     * 第二天切日重置后又会重新参与自动调度。</p>
     *
     * <p>状态写入全部按 id 条件更新，不修改调用方持有的实体：同步执行接口本身带事务，
     * 实体一旦变脏，提交时 Hibernate 会用整行回写覆盖这里的结果（删掉手动执行就重新调度）。</p>
     *
     * @param id 采集表 id
     */
    public void setRunning(long id)
    {
        etlTableRepo.markRunning(id, new Timestamp(System.currentTimeMillis()));
    }

    /**
     * 设置任务为已完成状态
     *
     * @param id       采集表 id
     * @param duration 本次耗时（秒）
     */
    @Transactional
    public void setFinished(long id, long duration)
    {
        // Conditional update (see EtlTableRepo.markFinished): a whole-row save would let a stale
        // executor overwrite a fresher state and would resurrect a row deleted mid-run (merge→INSERT).
        // duration must be part of the same guarded write, otherwise the list page keeps showing 0.
        etlTableRepo.markFinished(id, new Timestamp(System.currentTimeMillis()), duration);
    }

    /**
     * 设置任务为失败状态
     *
     * @param id       采集表 id
     * @param duration 本次耗时（秒）
     */
    @Transactional
    public void setFailed(long id, long duration)
    {
        // Conditional update: never downgrade a 'Y' written by a duplicate executor, and decrement
        // retryCnt against the row's current value rather than a possibly-stale snapshot.
        etlTableRepo.markFailed(id, new Timestamp(System.currentTimeMillis()), duration);
    }

    /**
     * etl_table.duration is NOT NULL, but the entity may come from a not-yet-persisted transient
     * object; fall back to 0 so the guarded update never writes null.
     */
    private long durationOf(EtlTable task)
    {
        return task.getDuration() == null ? 0L : task.getDuration();
    }

    /**
     * 设置任务为等待状态
     */
    public void setWaiting(EtlTable task)
    {
        task.setStatus(TableStatus.WAITING_COLLECT);
        etlTableRepo.save(task);
    }

    public void setNotCollect(EtlTable task)
    {
        task.setStatus(TableStatus.NOT_COLLECT);
        etlTableRepo.save(task);
    }

    /**
     * 刷新完成后把表置为"未采集"——但只在当前业务周期确实欠它一次采集时。
     *
     * <p>工作日/交易日源在休息日本来就不该采集（调度门禁会拒绝），这些表必须保留上一次采集的状态；
     * 否则切日刷新里这一步会把周末的表全部刷回 {@code N}，让它们看起来像"待采集"，
     * 看板成功率也跟着掉。重置（{@link #resetAllFlags()}）与这里的刷新是两条独立路径，必须用同一个判定。</p>
     */
    private void setNotCollectIfDue(EtlTable task)
    {
        java.time.LocalDate periodDate = calendarService.businessPeriodDate();
        if (calendarService.evaluate(task.getSourceCalendar(), periodDate).due()) {
            setNotCollect(task);
        }
        else {
            log.debug("日历 {} 中 {} 不是业务日，刷新后保持表 {} 的原状态", task.getSourceCalendar(), periodDate,
                task.getId());
        }
    }

    public void setUpdateSchema(long id)
    {
        etlTableRepo.markUpdateSchema(id);
    }

    /**
     * 获取所有可运行的任务
     *
     * @return 可运行的任务列表
     */
    public List<EtlTable> getRunnableTasks()
    {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        LocalTime currentTime = LocalDateTime.now().toLocalTime();
        boolean checkTime = currentTime.isAfter(switchTime);
        return etlTableRepo.findRunnableTasks(switchTime, currentTime, checkTime);
    }

    /**
     * 根据数据源ID获取可运行的任务
     *
     * @param sourceId 数据源ID
     * @return 可运行的任务列表
     */
    public List<EtlTable> getRunnableTasks(int sourceId)
    {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        LocalTime currentTime = LocalDateTime.now().toLocalTime();
        boolean checkTime = currentTime.isAfter(switchTime);
        return etlTableRepo.findRunnableTasks(switchTime, currentTime, checkTime)
            .stream()
            .filter(t -> t.getSid() == sourceId)
            .toList();
    }

    /**
     * 查询某个 source 下“继承调度”的可运行任务（表 startAt 为空）。
     */
    public List<EtlTable> getRunnableInheritedTasksBySource(int sourceId)
    {
        return etlTableRepo.findRunnableInheritedTasksBySource(sourceId);
    }

    /**
     * 查询“表级覆盖调度”的可运行任务（表 startAt 等于指定时间点）。
     */
    public List<EtlTable> getRunnableOverrideTasksByStartAt(LocalTime startAt)
    {
        return etlTableRepo.findRunnableOverrideTasksByStartAt(startAt);
    }

    public List<EtlTable> getRunnableOverrideTasksBetween(LocalTime from, LocalTime to)
    {
        return etlTableRepo.findRunnableOverrideTasksBetween(from, to);
    }

    /**
     * Collection calendar of the source a table belongs to; null when the table is gone (or has no source).
     */
    public String getSourceCalendar(long tid)
    {
        return etlTableRepo.findSourceCalendarByTid(tid);
    }

    /**
     * 获取有效表的数量
     *
     * @return 有效表的数量
     */
    public Integer getValidTableCount()
    {
        return etlTableRepo.findValidTableCount();
    }

    public long getAllTableCount()
    {
        return etlTableRepo.count();
    }

    /**
     * 获取所有有效的视图表
     *
     * @return 视图表列表
     */
    public List<VwEtlTableWithSource> getValidTableViews()
    {
        return vwEtlTableWithSourceRepo.findByEnabledTrueAndStatusNotAndRetiredAtIsNull(
            TableStatus.EXCLUDE_COLLECT);
    }

    /**
     * 重置所有表的标志位
     *
     * <p>只重置"今天确实有采集义务"的表：源配了工作日/交易日日历、而今天不是它的业务日时，
     * 调度层的门禁会拒绝采集。这类表必须保持上一次采集的状态（已采集的仍是 Y），否则它们在
     * 周末会一直显示未采集、看板成功率跟着掉，而且没有任何一次采集能把它们改回来。</p>
     */
    @Transactional
    public int resetAllFlags()
    {
        LocalDate today = LocalDate.now();
        List<String> dueCalendars = new ArrayList<>();
        for (String calendar : etlTableRepo.findCalendarsInUse()) {
            if (calendarService.evaluate(calendar, today).due()) {
                dueCalendars.add(calendar);
            }
            else {
                log.info("日历 {} 中 {} 不是业务日，跳过重置其表的采集状态", calendar, today);
            }
        }
        if (dueCalendars.isEmpty()) {
            log.info("今天不是任何日历的业务日，不重置任何表的采集状态");
            return 0;
        }
        return etlTableRepo.resetEtlFlagsForCalendars(dueCalendars);
    }

    /**
     * 查找特殊任务
     *
     * @return 特殊任务列表
     */
    /**
     * 获取指定数据源和库下的所有表
     */
    public List<String> getTablesBySidAndDb(int sid, String db)
    {
        return vwEtlTableWithSourceRepo.findBySidAndSourceDbAndRetiredAtIsNull(sid, db)
            .stream()
            .map(VwEtlTableWithSource::getSourceTable)
            .toList();
    }

    /**
     * Retire a table definition while preserving every historical fact.
     */
    @Transactional
    public void retireTable(long tableId, String username)
    {
        // Retirement must not race an in-flight worker that can still mutate the table status.
        List<EtlJobQueue> active = jobQueueService.findActiveByTid(tableId);
        if (!active.isEmpty()) {
            throw new ApiException(400, "表 " + tableId + " 存在运行中或排队中的采集任务，请先停止后再退役");
        }
        EtlTable table = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        if (table.getRetiredAt() == null) {
            table.setRetiredAt(LocalDateTime.now());
            table.setRetiredBy(username == null ? "" : username);
            etlTableRepo.save(table);
        }
    }

    @Transactional
    public void restoreTable(long tableId)
    {
        EtlTable table = etlTableRepo.findById(tableId)
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        if (table.getRetiredAt() == null) {
            throw new ApiException(400, "Table is not retired");
        }
        List<EtlJobQueue> active = jobQueueService.findActiveByTid(tableId);
        if (!active.isEmpty()) {
            throw new ApiException(409, "Table has an active queued or running job");
        }
        // Restore reuses this row; reject if a legacy duplicate already occupies the route.
        collectionScopeService.assertRestorable(table);
        table.setRetiredAt(null);
        table.setRetiredBy(null);
        etlTableRepo.save(table);
    }

    /**
     * 创建新的采集表
     *
     * @param etl 采集表对象
     * @return 创建的采集表对象
     */
    @Transactional
    public EtlTable createTable(EtlTable etl)
    {
        collectionScopeService.assertCreatable(etl);
        return etlTableRepo.save(etl);
    }

    @Transactional
    public EtlTable updateTable(EtlTable etl, String username)
    {
        EtlTable existing = etlTableRepo.findById(etl.getId())
            .orElseThrow(() -> new ApiException(404, "Table not found"));
        // Keep omitted route fields from silently moving the definition to the default target.
        if (etl.getSid() == null) {
            etl.setSid(existing.getSid());
        }
        if (etl.getTargetId() == null) {
            etl.setTargetId(existing.getTargetId());
        }
        if (routeChanged(existing, etl)) {
            collectionScopeService.assertRouteUnique(etl, etl.getId());
        }
        setChangedBy(username);
        return etlTableRepo.save(etl);
    }

    private boolean routeChanged(EtlTable before, EtlTable after)
    {
        return !Objects.equals(before.getSid(), after.getSid())
            || !Objects.equals(before.getSourceDb(), after.getSourceDb())
            || !Objects.equals(before.getSourceTable(), after.getSourceTable())
            || !Objects.equals(before.getTargetId(), after.getTargetId())
            || !Objects.equals(before.getTargetDb(), after.getTargetDb())
            || !Objects.equals(before.getTargetTable(), after.getTargetTable());
    }

    private void setChangedBy(String username)
    {
        String safeUsername = username == null ? "" : username;
        entityManager
            .createNativeQuery("select set_config('addax.changed_by', :username, true)")
            .setParameter("username", safeUsername)
            .getSingleResult();
    }

    /**
     * 批量创建采集表
     *
     * @param tables 采集表对象列表
     * @return 创建的采集表对象列表
     */
    @Transactional
    public List<EtlTable> batchCreateTable(List<EtlTable> tables)
    {
        for (EtlTable table : tables) {
            collectionScopeService.assertCreatable(table);
        }
        return etlTableRepo.saveAll(tables);
    }

    /**
     * 根据数据源ID获取采集表数量
     *
     * @param sid source id
     */
    public int getTableCountBySourceId(int sid)
    {
        return etlTableRepo.countBySid(sid);
    }

    @Transactional
    public void updateTableStatuses(BatchTableStatusDto params)
    {
        if (params.tids().isEmpty()) {
            return;
        }

        etlTableRepo.batchUpdateStatusAndFlag(params.tids(), params.status(), params.retryCnt());
    }
}
