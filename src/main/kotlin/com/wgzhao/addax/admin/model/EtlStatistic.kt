package com.wgzhao.addax.admin.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "etl_statistic")
data class EtlStatistic(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "tid")
    var tid: Long? = null,

    @Column(name = "start_at")
    var startAt: LocalDateTime? = null,

    @Column(name = "end_at")
    var endAt: LocalDateTime? = null,

    @Column(name = "take_secs")
    var takeSecs: Long? = null,

    @Column(name = "total_bytes")
    var totalBytes: Long? = null,

    @Column(name = "byte_speed")
    var byteSpeed: Long? = null,

    @Column(name = "rec_speed")
    var recSpeed: Long? = null,

    @Column(name = "total_recs")
    var totalRecs: Long? = null,

    @Column(name = "total_errors")
    var totalErrors: Long? = null,

    @Column(name = "run_date")
    var runDate: LocalDate? = null
)
