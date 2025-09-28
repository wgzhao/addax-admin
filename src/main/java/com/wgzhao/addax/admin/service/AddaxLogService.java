package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.AddaxLogDto;
import com.wgzhao.addax.admin.model.AddaxLog;
import com.wgzhao.addax.admin.model.EtlStatistic;
import com.wgzhao.addax.admin.model.TbAddaxSta;
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

    public void insertLog(long tid,  String message) {
        AddaxLog addaxLog = new AddaxLog();
        addaxLog.setTid(tid);
        addaxLog.setLog(message);
        addaxLog.setRunDate(LocalDate.now());
        addaxLog.setRunAt(LocalDateTime.now());
        addaxLogRepo.save(addaxLog);
    }

    public List<LocalDate> getLast5RunDatesByTid(long tid) {
        return addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid).stream()
                .map(AddaxLog::getRunDate)
                .distinct()
                .toList();
    }
    public AddaxLog getLastLogByTid(long tid) {
        return addaxLogRepo.findFirstByTidOrderByRunDateDesc(tid);
    }

    public List<AddaxLog> getLast5LogsById(long tid) {
        return addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid);
    }

    public String getLogContent(long id) {
        return addaxLogRepo.findLogById(id);
    }

    public List<AddaxLogDto> getLogEntry(String tid)
    {
        return addaxLogRepo.findLogEntry(tid);
    }

}
