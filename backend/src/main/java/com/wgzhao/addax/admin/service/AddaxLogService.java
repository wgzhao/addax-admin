package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.repository.AddaxLogRepo;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class AddaxLogService
{
    private final AddaxLogRepo addaxLogRepo;

    public void insertLog(long tid, String message)
    {
        AddaxLog addaxLog = new AddaxLog();
        addaxLog.setTid(tid);
        addaxLog.setLog(message);
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
}
