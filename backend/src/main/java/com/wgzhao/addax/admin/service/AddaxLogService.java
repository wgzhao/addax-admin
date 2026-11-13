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

    public Long insertLog(long tid, String message)
    {
        return insertLog(tid, null, message);
    }

    /**
     * @return the stored log id, so a failure fact can point at the exact run's output
     */
    public Long insertLog(long tid, Long executionId, String message)
    {
        AddaxLog addaxLog = new AddaxLog();
        addaxLog.setTid(tid);
        addaxLog.setExecutionId(executionId);
        // PostgreSQL text cannot store NUL (0x00); source data or a torn log file
        // may smuggle it in, so strip it at the persistence boundary
        addaxLog.setLog(message == null ? null : message.replace("\u0000", ""));
        addaxLog.setRunDate(LocalDate.now());
        addaxLog.setRunAt(LocalDateTime.now());
        return addaxLogRepo.save(addaxLog).getId();
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

    /**
     * @param allowedSourceIds null means every source; an empty set returns an empty page without
     *                         touching the database.
     */
    public Page<AddaxLog> listWithPage(int page, int pageSize, java.util.Collection<Integer> allowedSourceIds)
    {
        Pageable pageRequest = PageRequest.of(page, pageSize);
        if (allowedSourceIds == null) {
            return addaxLogRepo.findAllByOrderByIdDesc(pageRequest);
        }
        if (allowedSourceIds.isEmpty()) {
            return Page.empty(pageRequest);
        }
        return addaxLogRepo.findBySourceIds(allowedSourceIds, pageRequest);
    }

    public Long findTidById(long id)
    {
        return addaxLogRepo.findTidById(id);
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
