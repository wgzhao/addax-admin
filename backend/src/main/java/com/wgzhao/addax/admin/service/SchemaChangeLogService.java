package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.model.SchemaChangeLog;
import com.wgzhao.addax.admin.repository.SchemaChangeLogRepo;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class SchemaChangeLogService
{
    private final SchemaChangeLogRepo repo;

    public void recordAdd(Long tid, String sourceDb, String sourceTable, String columnName, String newSourceType, Integer newLen, Integer newPrecision, Integer newScale, String newComment)
    {
        SchemaChangeLog r = new SchemaChangeLog();
        r.setTid(tid);
        r.setSourceDb(sourceDb);
        r.setSourceTable(sourceTable);
        r.setColumnName(columnName);
        r.setChangeType("ADD");
        r.setNewSourceType(newSourceType);
        r.setNewDataLength(newLen);
        r.setNewDataPrecision(newPrecision);
        r.setNewDataScale(newScale);
        r.setNewColComment(newComment);
        r.setChangeAt(LocalDateTime.now());
        repo.save(r);
    }

    public void recordDelete(Long tid, String sourceDb, String sourceTable, String columnName, String oldSourceType, Integer oldLen, Integer oldPrecision, Integer oldScale, String oldComment)
    {
        SchemaChangeLog r = new SchemaChangeLog();
        r.setTid(tid);
        r.setSourceDb(sourceDb);
        r.setSourceTable(sourceTable);
        r.setColumnName(columnName);
        r.setChangeType("DELETE");
        r.setOldSourceType(oldSourceType);
        r.setOldDataLength(oldLen);
        r.setOldDataPrecision(oldPrecision);
        r.setOldDataScale(oldScale);
        r.setOldColComment(oldComment);
        r.setChangeAt(LocalDateTime.now());
        repo.save(r);
    }

    public void recordTypeChange(Long tid, String sourceDb, String sourceTable, String columnName, String oldType, String newType,
        Integer oldLen, Integer newLen, Integer oldPrecision, Integer newPrecision, Integer oldScale, Integer newScale,
        String oldComment, String newComment)
    {
        SchemaChangeLog r = new SchemaChangeLog();
        r.setTid(tid);
        r.setSourceDb(sourceDb);
        r.setSourceTable(sourceTable);
        r.setColumnName(columnName);
        r.setChangeType("TYPE_CHANGE");
        r.setOldSourceType(oldType);
        r.setNewSourceType(newType);
        r.setOldDataLength(oldLen);
        r.setNewDataLength(newLen);
        r.setOldDataPrecision(oldPrecision);
        r.setNewDataPrecision(newPrecision);
        r.setOldDataScale(oldScale);
        r.setNewDataScale(newScale);
        r.setOldColComment(oldComment);
        r.setNewColComment(newComment);
        r.setChangeAt(LocalDateTime.now());
        repo.save(r);
    }

    public List<SchemaChangeLog> findByTidBetweenDates(long tid, LocalDate start, LocalDate end)
    {
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.plusDays(1).atStartOfDay();
        return repo.findByTidAndChangeAtBetweenOrderByChangeAtDesc(tid, startDt, endDt);
    }

    public List<SchemaChangeLog> getAllFieldChanges()
    {
        return repo.findAll();
    }

    public Page<SchemaChangeLog> getFieldChanges(int page, int size)
    {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        return repo.findAll(pageable);
    }
}
