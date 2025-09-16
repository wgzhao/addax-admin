package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.repository.AddaxLogRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AddaxLogService
{
    @Autowired
    private AddaxLogRepo addaxLogRepo;

    public void insertLog(String tid,  String message) {
        AddaxLog addaxLog = new AddaxLog();
        addaxLog.setTid(tid);
        addaxLog.setLog(message);
        addaxLog.setRunDate(LocalDate.now());
        addaxLog.setRunAt(LocalDateTime.now());
        addaxLogRepo.save(addaxLog);
    }

    public List<LocalDate> getLast5RunDatesByTid(String tid) {
        return addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid).stream()
                .map(AddaxLog::getRunDate)
                .distinct()
                .toList();
    }
    public AddaxLog getLastLogByTid(String tid) {
        return addaxLogRepo.findFirstByTidOrderByRunDateDesc(tid);
    }

    public List<AddaxLog> getLast5LogsById(String tid) {
        return addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid);
    }

    public Optional<AddaxLog> getLogContent(long id) {
        return addaxLogRepo.findById(id);
    }

    public List<AddaxLogDto> getLogEntry(String tid)
    {
        return addaxLogRepo.findLogEntry(tid);
    }
}
