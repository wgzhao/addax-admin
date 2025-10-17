package com.wgzhao.addax.admin.model

import jakarta.persistence.*

@Entity
@Table(name = "vw_addax_log", schema = "public", catalog = "stg01")
data class VwAddaxLog(
    @Id
    @Basic
    @Column(name = "start_day")
    var startDay: String? = null,

    @Basic
    @Column(name = "spname")
    var spname: String? = null,

    @Basic
    @Column(name = "start_time")
    var startTime: String? = null,

    @Basic
    @Column(name = "end_time")
    var endTime: String? = null,

    @Basic
    @Column(name = "runtime")
    var runtime: Int? = null,

    @Basic
    @Column(name = "byte_speed")
    var byteSpeed: Int? = null,

    @Basic
    @Column(name = "rec_speed")
    var recSpeed: Int? = null,

    @Basic
    @Column(name = "total_rec")
    var totalRec: Int? = null,

    @Basic
    @Column(name = "total_err")
    var totalErr: Int? = null
)
