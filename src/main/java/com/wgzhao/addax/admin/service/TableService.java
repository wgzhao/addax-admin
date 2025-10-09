package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.common.TableStatus;
import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.utils.QueryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import static java.lang.Math.max;

/**
 * 采集表信息服务类，负责采集表的增删改查及资源刷新等业务操作
 */
@Service
@Slf4j
public class TableService
{
    @Autowired private EtlTableRepo etlTableRepo;
    @Autowired private ColumnService columnService;
    @Autowired private JobContentService jobContentService;
    @Autowired private DictService dictService;
    @Autowired private EtlJourService jourService;
    @Autowired private VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;
    @Autowired private TargetService targetService;

    /**
     * 刷新指定采集表的资源（如字段、模板等）
     * @param table 采集表对象
     * @return 任务结果
     */
    public TaskResultDto refreshTableResources(EtlTable table)
    {
        if (table == null) {
            return TaskResultDto.failure("Table is null", 0);
        }
        VwEtlTableWithSource vwTable = vwEtlTableWithSourceRepo.findById(table.getId()).orElse(null);
        if (vwTable == null) {
            log.warn("Table view not found for tid {}", table.getId());
            return TaskResultDto.failure("Table view not found for tid " + table.getId(), 0);
        }
        // 1. 更新列信息
        int retCode = columnService.updateTableColumns(vwTable);

        if (retCode == -1) {
            setStatus(table, TableStatus.COLLECT_FAIL);
            log.warn("Failed to update columns for table id {}", table.getId());
            return TaskResultDto.failure("Failed to update columns for table id " + table.getId(), 0);
        }

        if (retCode == 0) {
            // 检查表结构是否已经更新
            if (Objects.equals(vwTable.getStatus(), TableStatus.WAIT_SCHEMA)) {
                if (!targetService.createOrUpdateHiveTable(vwTable)) {
                    setStatus(table, TableStatus.COLLECT_FAIL);
                    log.warn("Failed to create or update Hive table for tid {}", table.getId());
                    return TaskResultDto.failure("Failed to create or update Hive table for tid " + table.getId(), 0);
                }
            }
            setStatus(table, TableStatus.NOT_COLLECT);
            return TaskResultDto.success("No columns updated for table id " + table.getId(), 0);
        }

        if (!targetService.createOrUpdateHiveTable(vwTable)) {
            setStatus(table, TableStatus.WAIT_SCHEMA);
            log.warn("Failed to create or update Hive table for tid {}", table.getId());
            return TaskResultDto.failure("Failed to create or update Hive table for tid " + table.getId(), 0);
        }

        // 2. 更新任务文件
        TaskResultDto result = jobContentService.updateJob(vwTable);
        if (result.isSuccess()) {
            setStatus(table, TableStatus.NOT_COLLECT);
            return TaskResultDto.success("Table resources refreshed successfully ", 0);
        }
        else {
            setStatus(table, TableStatus.COLLECT_FAIL);
            log.warn("Failed to update job content for tid {}", table.getId());
            return TaskResultDto.failure("Failed to update job content for tid " + table.getId(), 0);
        }
    }

    /**
     * 刷新指定ID的采集表资源
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
     * @param table 采集表对象
     */
    @Async
    public void refreshTableResourcesAsync(EtlTable table)
    {
        refreshTableResources(table);
    }

    /**
     * 刷新所有采集表的资源
     */
    public void refreshAllTableResources()
    {
        List<EtlTable> tables = etlTableRepo.findAll();
        for (EtlTable table : tables) {
            try {
                refreshTableResources(table);
            }
            catch (Exception e) {
                log.error("Failed to refresh resources for table {}", table.getId(), e);
            }
        }
    }

    /**
     * ODS 采集信息
     *
     */
    public Page<VwEtlTableWithSource> getVwTablesInfo(int page, int pageSize, String q, String sortField, String sortOrder)
    {

        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        if (q != null && !q.isEmpty()) {
            return vwEtlTableWithSourceRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        }
        else {
            return vwEtlTableWithSourceRepo.findAll(pageable);
        }
    }

    /**
     * 根据状态获取视图表信息
     * @param page 页码
     * @param pageSize 每页大小
     * @param q 查询关键字
     * @param status 表状态
     * @param sortField 排序字段
     * @param sortOrder 排序方式
     * @return 视图表信息分页结果
     */
    public Page<VwEtlTableWithSource> getVwTablesByStatus(int page, int pageSize, String q, String status, String sortField, String sortOrder)
    {
        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        return vwEtlTableWithSourceRepo.findByStatusAndFilterColumnContaining(status, q.toUpperCase(), pageable);
    }

    /**
     * 获取单个表的详细信息
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
     * @return 待采集任务数量
     */
    public int findPendingTasks()
    {
        return etlTableRepo.countByStatusEquals(TableStatus.NOT_COLLECT);
    }

    /**
     * 统计所有正在运行的任务数量
     * @return 正在运行的任务数量
     */
    public int findRunningTasks()
    {
        return etlTableRepo.countByStatusEquals(TableStatus.COLLECTING);
    }

    /**
     * 根据ID获取表及其数据源信息
     * @param tid 表ID
     * @return 表对象
     */
    public EtlTable getTableAndSource(long tid)
    {
        return etlTableRepo.findById(tid).orElse(null);
    }

    /**
     * 根据ID获取表信息
     * @param tid 表ID
     * @return 表对象
     */
    public EtlTable getTable(long tid)
    {
        return etlTableRepo.findById(tid).orElse(null);
    }

    /**
     * 根据ID获取视图表信息
     * @param tid 表ID
     * @return 视图表对象
     */
    public VwEtlTableWithSource getTableView(long tid)
    {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null);
    }

    /**
     * 设置任务为正在运行状态
     * @param task 任务对象
     */
    public void setRunning(EtlTable task)
    {
        task.setStatus(TableStatus.COLLECTING);
        task.setStartTime(new Timestamp(System.currentTimeMillis()));
        etlTableRepo.save(task);
    }

    /**
     * 设置任务为已完成状态
     * @param task 任务对象
     */
    public void setFinished(EtlTable task)
    {
        task.setStatus(TableStatus.COLLECTED);
        // 重试次数也重置
        task.setRetryCnt(3);
        task.setEndTime(new Timestamp(System.currentTimeMillis()));
        etlTableRepo.save(task);
    }

    /**
     * 设置任务为失败状态
     * @param task 任务对象
     */
    public void setFailed(EtlTable task)
    {
        task.setStatus(TableStatus.COLLECT_FAIL);
        task.setEndTime(new Timestamp(System.currentTimeMillis()));
        task.setRetryCnt(max(task.getRetryCnt() - 1, 0));
        etlTableRepo.save(task);
    }

    /**
     * 设置任务状态
     * @param table 任务对象
     * @param status 状态值
     */
    public void setStatus(EtlTable table, String status)
    {
        table.setStatus(status);
        etlTableRepo.save(table);
    }

    // 找到所有可以运行的任务
    // 要注意切日的问题
    /**
     * 获取所有可运行的任务
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
     * 获取有效表的数量
     * @return 有效表的数量
     */
    public Integer getValidTableCount()
    {
        return etlTableRepo.findValidTableCount();
    }

    /**
     * 获取所有有效表
     * @return 有效表列表
     */
    public List<EtlTable> getValidTables()
    {
        return etlTableRepo.findValidTables();
    }

    /**
     * 获取所有有效的视图表
     * @return 视图表列表
     */
    public List<VwEtlTableWithSource> getValidTableViews()
    {
        return vwEtlTableWithSourceRepo.findByEnabledTrueAndStatusNot(TableStatus.EXCLUDE_COLLECT);
    }

    /**
     * 重置所有表的标志位
     */
    @Transactional
    public void resetAllFlags()
    {
        etlTableRepo.resetAllEtlFlags();
    }

    /**
     * 查找特殊任务
     * @return 特殊任务列表
     */
    public List<EtlTable> findSpecialTasks()
    {
        return etlTableRepo.findSpecialTasks();
    }

    /**
     * 获取指定数据源和库下的所有表
     */
    public List<String> getTablesBySidAndDb(int sid, String db)
    {
        return vwEtlTableWithSourceRepo.findBySidAndSourceDb(sid, db)
                .stream()
                .map(VwEtlTableWithSource::getSourceTable)
                .toList();
    }

    /**
     * 删除指定ID的表及其相关信息
     * @param tableId 表ID
     */
    @Transactional
    public void deleteTable(long tableId)
    {
        // 首先删除列信息
        columnService.deleteByTid(tableId);
        // 然后删除任务信息
        jobContentService.deleteByTid(tableId);
        // 删除流水
        jourService.deleteByTid(tableId);
        // 最后删除采集表信息
        etlTableRepo.deleteById(tableId);
    }

    /**
     * 创建新的采集表
     * @param etl 采集表对象
     * @return 创建的采集表对象
     */
    public EtlTable createTable(EtlTable etl)
    {
        return etlTableRepo.save(etl);
    }

    /**
     * 批量创建采集表
     * @param tables 采集表对象列表
     * @return 创建的采集表对象列表
     */
    public List<EtlTable> batchCreateTable(List<EtlTable> tables)
    {
        return etlTableRepo.saveAll(tables);
    }
}
