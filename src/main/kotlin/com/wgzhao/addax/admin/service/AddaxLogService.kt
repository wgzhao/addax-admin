package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.dto.AddaxLogDto
import com.wgzhao.addax.admin.model.AddaxLog
import com.wgzhao.addax.admin.repository.AddaxLogRepo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class AddaxLogService(
    private val addaxLogRepo: AddaxLogRepo
) {
    private val log = LoggerFactory.getLogger(AddaxLogService::class.java)

    fun insertLog(tid: Long, message: String?) {
        val addaxLog = AddaxLog().apply {
            setTid(tid)
            setLog(message)
            setRunDate(LocalDate.now())
            setRunAt(LocalDateTime.now())
        }
        addaxLogRepo.save(addaxLog)
    }

    fun getLast5RunDatesByTid(tid: Long): List<LocalDate> =
        addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid)?.mapNotNull { it.runDate }?.distinct() ?: emptyList()

    fun getLastLogByTid(tid: Long): AddaxLog? =
        addaxLogRepo.findFirstByTidOrderByRunDateDesc(tid)

    fun getLast5LogsById(tid: Long): List<AddaxLog> =
        addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid) ?: emptyList()

    fun getLogContent(id: Long): String? =
        addaxLogRepo.findLogById(id)

    fun getLogEntry(tid: String?): List<AddaxLogDto> =
        addaxLogRepo.findLogEntry(tid) ?: emptyList()
}
