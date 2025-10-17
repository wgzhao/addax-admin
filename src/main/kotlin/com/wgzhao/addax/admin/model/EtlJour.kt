package com.wgzhao.addax.admin.model

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@Entity
@Table(name = "etl_jour")
data class EtlJour(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "tid")
    var tid: Long? = null,

    @Column(name = "kind", length = 32)
    var kind: String? = null,

    @Column(name = "start_at")
    var startAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "status")
    var status: Boolean = false,

    @Column(name = "cmd")
    var cmd: String? = null,

    @Column(name = "duration")
    var duration: Long = 0L,

    @Column(name = "error_msg", length = 4000)
    var errorMsg: String? = null
) : Serializable
