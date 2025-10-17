package com.wgzhao.addax.admin.model

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.util.*

/**
 * 数据中心消息提醒总表 实体类
 */
@Entity
@Table(name = "notification")
data class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,

    @Column(name = "phone")
    var phone: String? = null,

    @Column(name = "msg")
    var msg: String? = null,

    @Column(name = "sms")
    var sms: String? = null,

    @Column(name = "im")
    var im: String? = null,

    @Column(name = "call")
    var call: String? = null,

    @Column(name = "create_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    var createAt: Date? = null
)
