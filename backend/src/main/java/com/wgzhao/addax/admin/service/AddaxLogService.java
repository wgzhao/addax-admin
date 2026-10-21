package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.repository.AddaxLogRepo;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddaxLogService
{
    private final AddaxLogRepo addaxLogRepo;
    private final int retentionDays;

    public AddaxLogService(AddaxLogRepo addaxLogRepo,
        @Value("${ADDAX_LOG_RETENTION_DAYS:180}") int retentionDays)
    {
        this.addaxLogRepo = addaxLogRepo;
        this.retentionDays = retentionDays;
    }

    public void insertLog(long tid, String message)
    {
        insertLog(tid, null, message);
    }

    public void insertLog(long tid, Long executionId, String message)
    {
        AddaxLog addaxLog = new AddaxLog();
        addaxLog.setTid(tid);
        addaxLog.setExecutionId(executionId);
        // PostgreSQL text cannot store NUL (0x00); source data or a torn log file
        // may smuggle it in, so strip it at the persistence boundary
        addaxLog.setLog(message == null ? null : message.replace("\u0000", ""));
        addaxLog.setRunDate(LocalDate.now());
        addaxLog.setRunAt(LocalDateTime.now());
        addaxLogRepo.save(addaxLog);
    }

    public String getLogContent(long id)
    {
        return addaxLogRepo.findLogById(id);
    }

    public List<AddaxLogDto> getLogEntry(String tid)
    {
        return addaxLogRepo.findLogEntry(tid);
    }

    public Page<AddaxLog> listWithPage(int page, int pageSize)
    {
        Pageable pageRequest = PageRequest.of(page, pageSize);
        return addaxLogRepo.findAllByOrderByIdDesc(pageRequest);
    }

    @Async
    @Transactional
    public void cleanupLogsBefore(LocalDate before) {
        addaxLogRepo.deleteByRunAtBefore(before);
    }

    /**
     * Raw Addax output is large and has a shorter lifecycle than execution facts.
     */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanupExpiredLogs()
    {
        LocalDate before = LocalDate.now().minusDays(Math.max(retentionDays, 1));
        addaxLogRepo.deleteByRunAtBefore(before);
    }
}
