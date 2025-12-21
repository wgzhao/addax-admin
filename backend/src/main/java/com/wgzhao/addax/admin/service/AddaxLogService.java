package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.repository.AddaxLogRepo;
import lombok.AllArgsConstructor;
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

    public List<LocalDate> getLast5RunDatesByTid(long tid)
    {
        return addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid).stream()
            .map(AddaxLog::getRunDate)
            .distinct()
            .toList();
    }

    public AddaxLog getLastLogByTid(long tid)
    {
        return addaxLogRepo.findFirstByTidOrderByRunDateDesc(tid);
    }

    public List<AddaxLog> getLast5LogsById(long tid)
    {
        return addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid);
    }

    public String getLogContent(long id)
    {
        return addaxLogRepo.findLogById(id);
    }

    public List<AddaxLogDto> getLogEntry(String tid)
    {
        return addaxLogRepo.findLogEntry(tid);
    }
}
