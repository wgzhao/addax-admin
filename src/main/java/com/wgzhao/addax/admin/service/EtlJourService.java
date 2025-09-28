package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.repository.EtlJourRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class EtlJourService
{
    private final EtlJourRepo etlJourRepo;

    // 新增一条流水
    @Transactional
    public EtlJour addJour(long taskId, JourKind jourKind, String cmd)
    {
        EtlJour jour = new EtlJour();
        jour.setTid(taskId);
        jour.setKind(jourKind.name());
        jour.setCmd(cmd);
        jour.setStartAt(LocalDateTime.now());
        return etlJourRepo.save(jour);
    }
    // 补充流水的状态信息
    @Transactional
    public void successJour(EtlJour jour)
    {
        jour.setStatus(true);
        jour.setDuration( Duration.between(jour.getStartAt(), LocalDateTime.now()).toSeconds());
        etlJourRepo.save(jour);
    }

    @Transactional
    public void failJour(EtlJour jour, String errorMsg)
    {
        jour.setStatus(false);
        jour.setErrorMsg(errorMsg);
        jour.setDuration(Duration.between(jour.getStartAt(), LocalDateTime.now()).toSeconds());
        etlJourRepo.save(jour);
    }

    public void saveJour(EtlJour etlJour)
    {
        etlJourRepo.save(etlJour);
    }
}
