package com.wgzhao.addax.admin.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "addax_log")
data class AddaxLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var tid: Long? = null,
    var runAt: LocalDateTime? = null,
    var runDate: LocalDate? = null,
    var log: String? = null
)
