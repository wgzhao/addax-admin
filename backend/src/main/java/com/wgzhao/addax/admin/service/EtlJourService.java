package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.JourKind;
import com.wgzhao.addax.admin.model.EtlJour;
import com.wgzhao.addax.admin.repository.EtlJourRepo;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 采集流水服务类，负责采集任务流水的记录与状态管理
 */
@Service
@AllArgsConstructor
public class EtlJourService
{
    private final EtlJourRepo etlJourRepo;

    /**
     * 新增一条采集流水
     *
     * @param taskId 任务ID
     * @param jourKind 流水类型
     * @param cmd 执行命令
     * @return 新增的流水对象
     */
    @Transactional
    public EtlJour addJour(long taskId, String jourKind, String cmd)
    {
        EtlJour jour = new EtlJour();
        jour.setTid(taskId);
        jour.setKind(jourKind);
        jour.setCmd(cmd);
        jour.setStartAt(LocalDateTime.now());
        return etlJourRepo.save(jour);
    }

    /**
     * 补充流水的成功状态信息
     *
     * @param jour 流水对象
     */
    @Transactional
    public void successJour(EtlJour jour)
    {
        jour.setStatus(true);
        jour.setDuration(Duration.between(jour.getStartAt(), LocalDateTime.now()).toSeconds());
        etlJourRepo.save(jour);
    }

    /**
     * 补充流水的失败状态信息
     *
     * @param jour 流水对象
     * @param errorMsg 错误信息
     */
    @Transactional
    public void failJour(EtlJour jour, String errorMsg)
    {
        jour.setStatus(false);
        jour.setErrorMsg(errorMsg);
        jour.setDuration(Duration.between(jour.getStartAt(), LocalDateTime.now()).toSeconds());
        etlJourRepo.save(jour);
    }

    /**
     * 保存流水对象
     *
     * @param etlJour 流水对象
     */
    public void saveJour(EtlJour etlJour)
    {
        etlJourRepo.save(etlJour);
    }

    /**
     * 根据表ID删除所有流水
     *
     * @param tableId 表ID
     */
    public void deleteByTid(long tableId)
    {
        etlJourRepo.deleteAllByTid(tableId);
    }

    /**
     * 查询指定表的最后一次错误信息
     *
     * @param tableId 表ID
     * @return 错误信息
     */
    public String findLastErrorByTableId(long tableId)
    {
        return etlJourRepo.findLastError(tableId);
    }

    public EtlJour getLastByTidWithKind(long tableId, String kind)
    {
        if (kind == null || kind.isEmpty()) {
            kind = JourKind.COLLECT;
        }
        return etlJourRepo.findFirstByTidAndKindOrderByIdDesc(tableId, kind).orElse(null);
    }
}
