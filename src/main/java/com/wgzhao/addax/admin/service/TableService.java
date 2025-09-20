package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.model.EtlSource;
import com.wgzhao.addax.admin.model.EtlTable;
import com.wgzhao.addax.admin.model.VwImpEtlWithDb;
import com.wgzhao.addax.admin.repository.EtlColumnRepo;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import com.wgzhao.addax.admin.repository.EtlSourceRepo;
import com.wgzhao.addax.admin.repository.EtlTableRepo;
import com.wgzhao.addax.admin.repository.VwImpEtlWithDbRepo;
import com.wgzhao.addax.admin.utils.DbUtil;
import com.wgzhao.addax.admin.utils.QueryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private VwImpEtlWithDbRepo vwImpEtlWithDbRepo;

    @Autowired
    private EtlSourceRepo etlSourceRepo;

    @Autowired
    private SysItemRepo sysItemRepo;

    @Autowired
    private TaskService taskService;
    @Autowired private EtlColumnRepo etlColumnRepo;
    @Autowired private SystemConfigService systemConfigService;
    @Autowired private DictService dictService;

    public Page<VwImpEtlWithDb> fetchEtlInfo(int page, int pageSize)
    {
        Pageable pageable = PageRequest.of(page, pageSize);
        return vwImpEtlWithDbRepo.findAll(pageable);
    }

    /**
     * ODS 采集信息
     *
     */
    public Page<VwImpEtlWithDb> getOdsInfo(int page, int pageSize, String q, String sortField, String sortOrder)
    {

        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        if (q != null && !q.isEmpty()) {
            System.out.println("search " + q.toUpperCase());
            return vwImpEtlWithDbRepo.findByFilterColumnContaining(q.toUpperCase(), pageable);
        }
        else {
            return vwImpEtlWithDbRepo.findAll(pageable);
        }
    }

    public Page<VwImpEtlWithDb> getOdsByFlag(int page, int pageSize, String q, String flag, String sortField, String sortOrder)
    {
        Pageable pageable = PageRequest.of(page, pageSize, QueryUtil.generateSort(sortField, sortOrder));
        return vwImpEtlWithDbRepo.findByFlagAndFilterColumnContaining(flag, q.toUpperCase(), pageable);
    }

    public VwImpEtlWithDb findOneODSInfo(String tid)
    {
        return vwImpEtlWithDbRepo.findById(tid).orElse(null);
    }

    /**
     * 添加表的字段信息到 etl_column 表，他包含了源表的字段信息和目标表的字段信息
     *
     * @param etlTable etl_table 表记录
     * @return true 成功， false 失败
     */
    public boolean addTableInfo(EtlTable etlTable)
    {
        if (etlTable == null) {
            return false;
        }
        // 获取数据库连接信息
        int sourceId = etlTable.getEtlSource().getId();
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
        List<SysItem> t = sysItemRepo.findByDictCode(2011);
        Map<String, String> hiveTypeMapping = new HashMap<>();
        for (SysItem dict : t) {
            hiveTypeMapping.put(dict.getItemKey(), dict.getItemValue());
        }

        String sql = "select * from `" + etlTable.getSourceDb() + "`.`" + etlTable.getSourceTable() + "` where 1=0";
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            EtlColumn etlColumn = new EtlColumn();
            etlColumn.setTid(etlTable.getId());

            for (int i = 1; i <= columnCount; i++) {
                etlColumn.setColumnId(i);
                etlColumn.setColumnName(metaData.getColumnName(i));
                etlColumn.setSourceType(metaData.getColumnTypeName(i));
                etlColumn.setDataLength(metaData.getColumnDisplaySize(i));
                etlColumn.setDataPrecision(metaData.getPrecision(i));
                etlColumn.setDataScale(metaData.getScale(i));

                // map SQL type to Hive type
                String hiveType = hiveTypeMapping.getOrDefault(metaData.getColumnTypeName(i), "string");
                etlColumn.setTargetType(hiveType);
                etlColumn.setTargetTypeFull(hiveType);
                etlColumnRepo.save(etlColumn);
            }
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public boolean addTableInfo()
    {
        List<EtlTable> etlList = etlTableRepo.findByBupdateOrBcreateIsY();
        List<Long> ids = etlList.stream().map(EtlTable::getId).toList();
        for (EtlTable etl : etlList) {
            if (!addTableInfo(etl)) {
                return false;
            }
            etl.setCreateFlag("N");
            etl.setUpdateFlag("N");
            etl.setStatus("N");
            etlTableRepo.save(etl);
        }
        // update job table
        taskService.updateJob(ids);
        return true;
    }

    public List<Map<String, Object>> findFieldsCompare(String tid)
    {
        return null;
    }

    public void updateStatusAndFlag(List<String> ids, String status, int retryCnt) {
        etlTableRepo.batchUpdateStatusAndFlag(ids, status, retryCnt);
    }

    // 找到所有需要采集的表
    public int findPendingTasks() {
        return etlTableRepo.countByStatusEquals("N");
    }

    public int findRunningTasks() {
        return etlTableRepo.countByStatusEquals("R");
    }

    public EtlTable getTableAndSource(long tid) {
        return etlTableRepo.findById(tid).orElse(null);
    }

    public EtlTable getTable(long tid)
    {
        return etlTableRepo.findById(tid).orElse(null);
    }

    public void setRunning(EtlTable task) {
        task.setStatus("R");
        task.setStartTime(new Timestamp(System.currentTimeMillis()));
        etlTableRepo.save(task);
    }

    public void setFinished(EtlTable task) {
        task.setStatus("Y");
        task.setEndTime(new Timestamp(System.currentTimeMillis()));
        etlTableRepo.save(task);
    }

    public void setFailed(EtlTable task)
    {
        task.setStatus("E");
        task.setEndTime(new Timestamp(System.currentTimeMillis()));
        task.setRetryCnt(task.getRetryCnt() - 1);
        etlTableRepo.save(task);
    }

    // 找到所有可以运行的任务
    // 要注意切日的问题
    public List<EtlTable> getRunnableTasks() {
        LocalTime switchTime = dictService.getSwitchTimeAsTime();
        LocalTime currentTime = LocalDateTime.now().toLocalTime();
        boolean checkTime = currentTime.isAfter(switchTime);
        return etlTableRepo.findRunnableTasks(switchTime, currentTime, checkTime);
    }
}
