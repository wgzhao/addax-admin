package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.TaskResultDto;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
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

import static java.lang.Math.max;

/**
 * 采集表表信息管理
 */
@Service
@Slf4j
public class TableService
{

    @Autowired
    private EtlTableRepo etlTableRepo;

    @Autowired
    private EtlSourceRepo etlSourceRepo;

    @Autowired private EtlColumnRepo etlColumnRepo;
    @Autowired private ColumnService columnService;
    @Autowired private JobContentService jobContentService;
    @Autowired private DictService dictService;
    @Autowired private EtlJourService jourService;

    @Autowired
    private VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;

    @Autowired
    private TargetService targetService;

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
        if (retCode == 0) {
            // 说明字段没有变化，那么就不需要继续后面的流程了
            table.setStatus("N"); //设置为待采集状态
            etlTableRepo.save(table);
            return TaskResultDto.success("No columns updated for table id " + table.getId(),0);
        }
        if (retCode == -1) {
            table.setStatus("E");
            log.warn("Failed to update columns for table id {}", table.getId());
            return TaskResultDto.failure("Failed to update columns for table id " + table.getId(), 0);
        }

        TaskResultDto result = targetService.createOrUpdateHiveTable(vwTable);
        if (!result.isSuccess()) {
            table.setStatus("E");
            etlTableRepo.save(table);
            log.warn("Failed to create or update Hive table for tid {}", table.getId());
            return TaskResultDto.failure("Failed to create or update Hive table for tid " + table.getId(), result.getDurationSeconds());
        }

        // 2. 更新任务文件
        result = jobContentService.updateJob(vwTable);
        if (result.isSuccess()) {
            table.setStatus("N");
            etlTableRepo.save(table);
            return TaskResultDto.success("Table resources refreshed successfully ", result.getDurationSeconds());
        }
        else {
            table.setStatus("E");
            etlTableRepo.save(table);
            log.warn("Failed to update job content for tid {}", table.getId());
            return TaskResultDto.failure("Failed to update job content for tid " + table.getId(), result.getDurationSeconds());
        }
    }

    public TaskResultDto refreshTableResources(long tableId)
    {
        EtlTable table = etlTableRepo.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with id: " + tableId));
        return refreshTableResources(table);
    }

    @Async
    public void refreshTableResourcesAsync(EtlTable table)
    {
        refreshTableResources(table);
    }

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

    public Page<VwEtlTableWithSource> fetchEtlInfo(int page, int pageSize)
    {
        Pageable pageable = PageRequest.of(page, pageSize);
        return vwEtlTableWithSourceRepo.findAll(pageable);
    }

    /**
     * ODS 采集信息
     *
     */
    public Page<VwEtlTableWithSource> getVwTablesInfo(int page, int pageSize, String q, String sortField, String sortOrder)
    {

        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        if (q != null && !q.isEmpty()) {
            System.out.println("search " + q.toUpperCase());
            return vwEtlTableWithSourceRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        }
        else {
            return vwEtlTableWithSourceRepo.findAll(pageable);
        }
    }

    public Page<VwEtlTableWithSource> getVwTablesByStatus(int page, int pageSize, String q, String status, String sortField, String sortOrder)
    {
        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        return vwEtlTableWithSourceRepo.findByStatusAndFilterColumnContaining(status, q.toUpperCase(), pageable);
    }

    public VwEtlTableWithSource findOneTableInfo(long tid)
    {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null);
    }

    // 找到所有需要采集的表
    public int findPendingTasks()
    {
        return etlTableRepo.countByStatusEquals("N");
    }

    public int findRunningTasks()
    {
        return etlTableRepo.countByStatusEquals("R");
    }

    public EtlTable getTableAndSource(long tid)
    {
        return etlTableRepo.findById(tid).orElse(null);
    }

    public EtlTable getTable(long tid)
    {
        return etlTableRepo.findById(tid).orElse(null);
    }

    public VwEtlTableWithSource getTableView(long tid)
    {
        return vwEtlTableWithSourceRepo.findById(tid).orElse(null);
    }

    public void setRunning(EtlTable task)
    {
        task.setStatus("R");
        task.setStartTime(new Timestamp(System.currentTimeMillis()));
        etlTableRepo.save(task);
    }

    public void setFinished(EtlTable task)
    {
        task.setStatus("Y");
        // 重试次数也重置
        task.setRetryCnt(3);
        task.setEndTime(new Timestamp(System.currentTimeMillis()));
        etlTableRepo.save(task);
    }

    public void setFailed(EtlTable task)
    {
        task.setStatus("E");
        task.setEndTime(new Timestamp(System.currentTimeMillis()));
        task.setRetryCnt(max(task.getRetryCnt() - 1, 0));
        etlTableRepo.save(task);
    }

    // 找到所有可以运行的任务
    // 要注意切日的问题
    public List<EtlTable> getRunnableTasks()
    {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        LocalTime currentTime = LocalDateTime.now().toLocalTime();
        boolean checkTime = currentTime.isAfter(switchTime);
        return etlTableRepo.findRunnableTasks(switchTime, currentTime, checkTime);
    }

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

    public Integer getValidTableCount()
    {
        return etlTableRepo.findValidTableCount();
    }

    public List<EtlTable> getValidTables()
    {
        return etlTableRepo.findValidTables();
    }

    public List<VwEtlTableWithSource> getValidTableViews()
    {
        return vwEtlTableWithSourceRepo.findByEnabledTrueAndStatusNot("X");
    }

    @Transactional
    public void resetAllFlags()
    {
        etlTableRepo.resetAllEtlFlags();
    }

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

    @Transactional
    public boolean deleteTable(long tableId)
    {
        // 首先删除列信息
        columnService.deleteByTid(tableId);
        // 然后删除任务信息
        jobContentService.deleteByTid(tableId);
        // 删除流水
        jourService.deleteByTid(tableId);
        // 最后删除采集表信息
        etlTableRepo.deleteById(tableId);
        return true;
    }

    public EtlTable createTable(EtlTable etl)
    {
        return etlTableRepo.save(etl);
    }

    public List<EtlTable> batchCreateTable(List<EtlTable> tables)
    {
        return etlTableRepo.saveAll(tables);
    }
}
