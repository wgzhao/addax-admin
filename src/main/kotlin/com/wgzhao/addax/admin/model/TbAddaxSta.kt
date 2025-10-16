package com.wgzhao.addax.admin.model

import jakarta.persistence.*
import java.sql.Timestamp

/**
 * Addax作业统计信息实体类。
 * 用于存储作业运行的时间、速率、数据量等统计信息。
 */
@Entity
@Table(name = "tb_addax_sta")
data class TbAddaxSta(
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "pkid")
    var pkid: String? = null,

    @Basic
    @Column(name = "jobname")
    var jobname: String? = null,

    @Basic
    @Column(name = "start_ts")
    var startTs: Int? = null,

    @Basic
    @Column(name = "end_ts")
    var endTs: Int? = null,

    @Basic
    @Column(name = "take_secs")
    var takeSecs: Int? = null,

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
    @Column(name = "total_bytes")
    var totalBytes: Long? = null,

    @Basic
    @Column(name = "total_err")
    var totalErr: Int? = null,

    @Basic
    @Column(name = "updt_date")
    var updtDate: Timestamp? = null
)
