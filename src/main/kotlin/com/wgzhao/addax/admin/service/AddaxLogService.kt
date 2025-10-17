package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.dto.AddaxLogDto
import com.wgzhao.addax.admin.model.AddaxLog
import com.wgzhao.addax.admin.repository.AddaxLogRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class AddaxLogService(
    private val addaxLogRepo: AddaxLogRepo
) {
    private val log = KotlinLogging.logger {}

    fun insertLog(tid: Long, message: String?) {
        val addaxLog = AddaxLog(tid=tid, log=message, runDate = LocalDate.now(), runAt = LocalDateTime.now() )
        addaxLogRepo.save(addaxLog)
    }

    fun getLast5RunDatesByTid(tid: Long): List<LocalDate?>? =
        addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid)?.mapNotNull { it?.runDate }

    fun getLastLogByTid(tid: Long): AddaxLog? =
        addaxLogRepo.findFirstByTidOrderByRunDateDesc(tid)

    fun getLast5LogsById(tid: Long): List<AddaxLog?>? =
        addaxLogRepo.findTop5ByTidOrderByRunDateDesc(tid)

    fun getLogContent(id: Long): String? =
        addaxLogRepo.findById(id).orElse(null)?.log

    fun getLogEntry(tid: Long?): List<AddaxLogDto> =
        addaxLogRepo.findTop5ByTidOrderByRunAtDesc(tid) ?: emptyList()
}
