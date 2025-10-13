package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.model.EtlJour
import com.wgzhao.addax.admin.repository.EtlJourRepo
import lombok.AllArgsConstructor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/**
 * 采集流水服务类，负责采集任务流水的记录与状态管理
 */
@Service
@AllArgsConstructor
class EtlJourService {
    private val etlJourRepo: EtlJourRepo? = null

    /**
     * 新增一条采集流水
     * @param taskId 任务ID
     * @param jourKind 流水类型
     * @param cmd 执行命令
     * @return 新增的流水对象
     */
    @Transactional
    fun addJour(taskId: Long, jourKind: String?, cmd: String?): EtlJour {
        val jour = EtlJour()
        jour.setTid(taskId)
        jour.setKind(jourKind)
        jour.setCmd(cmd)
        jour.setStartAt(LocalDateTime.now())
        return etlJourRepo!!.save<EtlJour>(jour)
    }

    /**
     * 补充流水的成功状态信息
     * @param jour 流水对象
     */
    @Transactional
    fun successJour(jour: EtlJour) {
        jour.setStatus(true)
        jour.setDuration(Duration.between(jour.getStartAt(), LocalDateTime.now()).toSeconds())
        etlJourRepo!!.save<EtlJour?>(jour)
    }

    /**
     * 补充流水的失败状态信息
     * @param jour 流水对象
     * @param errorMsg 错误信息
     */
    @Transactional
    fun failJour(jour: EtlJour, errorMsg: String?) {
        jour.setStatus(false)
        jour.setErrorMsg(errorMsg)
        jour.setDuration(Duration.between(jour.getStartAt(), LocalDateTime.now()).toSeconds())
        etlJourRepo!!.save<EtlJour?>(jour)
    }

    /**
     * 保存流水对象
     * @param etlJour 流水对象
     */
    fun saveJour(etlJour: EtlJour) {
        etlJourRepo!!.save<EtlJour?>(etlJour)
    }

    /**
     * 根据表ID删除所有流水
     * @param tableId 表ID
     */
    fun deleteByTid(tableId: Long) {
        etlJourRepo!!.deleteAllByTid(tableId)
    }

    /**
     * 查询指定表的最后一次错误信息
     * @param tableId 表ID
     * @return 错误信息
     */
    fun findLastErrorByTableId(tableId: Long): String? {
        return etlJourRepo!!.findLastError(tableId)
    }

    fun getErrorKindByTid(tableId: Long): String? {
        return etlJourRepo!!.findFirstByTidAndStatusIsFalse(tableId)!!.map<Any?>(EtlJour::getKind).orElse(null)
    }
}
