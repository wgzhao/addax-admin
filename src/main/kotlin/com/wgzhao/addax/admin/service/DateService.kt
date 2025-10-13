package com.wgzhao.addax.admin.service

import com.wgzhao.addax.admin.repository.SysDictRepo
import com.wgzhao.addax.admin.repository.SysItemRepo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class DateService {
    @Autowired
    private val sysDictRepo: SysDictRepo? = null

    @Autowired
    private val sysItemRepo: SysItemRepo? = null

    val shortDate: String
        get() = LocalDate.now().format(sdf)

    val currDateTime: String
        get() {
            val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            return LocalDateTime.now().format(sdf)
        }

    companion object {
        private val sdf: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
