package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwEtlTableWithSource;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.VwEtlTableWithSourceRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.QueryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @Autowired
    private VwEtlTableWithSourceRepo vwEtlTableWithSourceRepo;

    @Autowired
    private TargetService targetService;

    public void refreshTableResources(EtlTable table) {
        if (table == null) {
            return;
        }
        VwEtlTableWithSource vwTable = vwEtlTableWithSourceRepo.findById(table.getId()).orElse(null);
        if (vwTable == null) {
            log.warn("Table view not found for tid {}", table.getId());
            return;
        }
        // 1. 更新列信息
        table.setUpdateFlag("y"); //正在更新
        if (columnService.updateTableColumns(vwTable) > 0) {
            table.setUpdateFlag("N"); //更新完成
            table.setStatus("N"); //设置为待采集状态
//            etlTableRepo.save(table);
            log.info("Updated columns for table id {}", table.getId());
            // 更新成功后，重建Hive表
            table.setCreateFlag("y");
//            etlTableRepo.save(table);
            if (!targetService.createOrUpdateHiveTable(vwTable)) {
                log.warn("Failed to update Hive table for tid {}", table.getId());
                // TODO: 这里是否应该继续后面的流程，后续手工只需要补救 Hive 表即可
                table.setCreateFlag("Y"); //创建失败
//                return;
            } else {
                table.setCreateFlag("N");
            }
            etlTableRepo.save(table);
            // 2. 更新任务文件
            jobContentService.updateJob(vwTable);
        }
    }

    public void refreshTableResources(long tableId) {
        EtlTable table = etlTableRepo.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found with id: " + tableId));
        refreshTableResources(table);
    }

    public void refreshAllTableResources() {
        List<EtlTable> tables = etlTableRepo.findAll();
        for (EtlTable table : tables) {
            try {
                refreshTableResources(table);
            } catch (Exception e) {
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



//    public void updateSchema(boolean isForce)
//    {
//        List<EtlTable> etlList;
//        if (isForce) {
//            etlList = etlTableRepo.findAll();
//        }
//        else {
//            etlList = etlTableRepo.findByCreateFlagOrUpdateFlag("Y", "Y");
//        }
//        for (EtlTable etl : etlList) {
//            if (!updateTableInfo(etl)) {
//                log.warn("failed to add table info for tid {}", etl.getId());
//                continue;
//            }
//            etl.setCreateFlag("N");
//            etl.setUpdateFlag("N");
//            etl.setStatus("N");
//            etlTableRepo.save(etl);
//            // update job table
//            jobContentService.updateJob(etl);
//        }
//    }

    /**
     * 异步更新表结构
     *
     * @param tid 为空表示更新所有表；不为空表示只更新指定表
     * @param mode "all" 表示全部强制更新；need 表示只更新需要更新的；tid 表示只更新指定表
     */
    @Async
    public void updateSchemaAsync(Long tid, String mode)
    {

        if (tid != null) {
            EtlTable etl = etlTableRepo.findById(tid).orElse(null);
            if (etl != null) {
                updateTableInfo(etl);
                etl.setCreateFlag("N");
                etl.setUpdateFlag("N");
                etl.setStatus("N");
                etlTableRepo.save(etl);
                jobContentService.updateJob(getTableView(tid));
            }
            return;
        }
        if ("need".equalsIgnoreCase(mode)) {
            // 默认只更新需要更新的表
            List<EtlTable> etlList = etlTableRepo.findByCreateFlagOrUpdateFlag("Y", "Y");
            for (EtlTable etl : etlList) {
                updateTableInfo(etl);
                etl.setCreateFlag("N");
                etl.setUpdateFlag("N");
                etl.setStatus("N");
                etlTableRepo.save(etl);
                jobContentService.updateJob(getTableView(etl.getId()));
            }
            return ;
        }
        if ("all".equalsIgnoreCase(mode)) {
            List<EtlTable> etlList = etlTableRepo.findAll();
            for (EtlTable etl : etlList) {
                updateTableInfo(etl);
                etl.setCreateFlag("N");
                etl.setUpdateFlag("N");
                etl.setStatus("N");
                etlTableRepo.save(etl);
                jobContentService.updateJob(getTableView(tid));
            }
            return;
        }

    }

//    public void updateStatusAndFlag(List<Long> ids, String status, int retryCnt)
//    {
//        List<EtlTable> tables = etlTableRepo.findAllById(ids);
//        if (tables.isEmpty()) {
//            return;
//        }
//        tables.forEach(etlTable -> {
//            etlTable.setStatus(status);
//            etlTable.setRetryCnt(retryCnt);
//        });
//        etlTableRepo.saveAll(tables);
////        etlTableRepo.batchUpdateStatusAndFlag(ids, status, retryCnt);
//    }

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

    /**
     * 添加表的字段信息到 etl_column 表，他包含了源表的字段信息和目标表的字段信息
     *
     * @param etlTable etl_table 表记录
     * @return true 成功， false 失败
     */
    public boolean updateTableInfo(EtlTable etlTable)
    {
        if (etlTable == null) {
            return false;
        }
        // 获取数据库连接信息
        int sourceId = etlTable.getSid();
        EtlSource dbInfo = etlSourceRepo.findById(sourceId).orElse(null);
        if (dbInfo == null) {
            log.warn("cannot find source info for id {}", sourceId);
            return false;
        }
        // 获取源表的字段信息
        Connection connection = DbUtil.getConnect(dbInfo.getUrl(), dbInfo.getUsername(), dbInfo.getPass());
        if (connection == null) {
            return false;
        }

        Map<String, String> hiveTypeMapping = dictService.getHiveTypeMapping();
        String sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            EtlColumn etlColumn = new EtlColumn();
            etlColumn.setTid(etlTable.getId());
            List<String> hiveColumns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                etlColumn.setColumnId(i);
                etlColumn.setColumnName(metaData.getColumnName(i));
                etlColumn.setSourceType(metaData.getColumnTypeName(i));
                etlColumn.setDataLength(metaData.getColumnDisplaySize(i));
                etlColumn.setDataPrecision(metaData.getPrecision(i));
                etlColumn.setDataScale(metaData.getScale(i));
                String colComment = DbUtil.getColumnComment(connection, etlTable.getSourceDb(), etlTable.getSourceTable(), metaData.getColumnName(i));
                etlColumn.setColComment(colComment);
                // map SQL type to Hive type
                String hiveType = hiveTypeMapping.getOrDefault(metaData.getColumnTypeName(i), "string");
                etlColumn.setTargetType(hiveType);
                if (Objects.equals(hiveType, "decimal")) {
                    // decimal(10,2)
                    hiveType = String.format("decimal(%d,%d)", metaData.getPrecision(i), metaData.getScale(i));
                }
                etlColumn.setTargetTypeFull(hiveType);
                hiveColumns.add("`" + metaData.getColumnName(i) + "` " + hiveType);
                etlColumnRepo.save(etlColumn);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}
