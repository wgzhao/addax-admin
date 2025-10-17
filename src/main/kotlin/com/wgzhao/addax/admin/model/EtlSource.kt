package com.wgzhao.addax.admin.model

import jakarta.persistence.*
import java.time.LocalTime

@Entity
@Table(name = "etl_source")
data class EtlSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int = 0,

    @Column(name = "code", length = 10, nullable = false)
    var code: String,

    @Column(name = "name", length = 200, nullable = false)
    var name: String? = null,

    @Column(name = "url", length = 500, nullable = false)
    var url: String? = null,

    @Column(name = "username", length = 64)
    var username: String? = null,

    @Column(name = "pass", length = 64)
    var pass: String? = null,

    @Column(name = "start_at")
    var startAt: LocalTime? = null,

    @Column(name = "prerequisite", length = 4000)
    var prerequisite: String? = null,

    @Column(name = "pre_script", length = 4000)
    var preScript: String? = null,

    @Column(name = "remark", length = 2000)
    var remark: String? = null,

    @Column(name = "enabled")
    var enabled: Boolean = false
)
