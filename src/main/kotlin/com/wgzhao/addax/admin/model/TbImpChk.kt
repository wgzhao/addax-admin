package com.wgzhao.addax.admin.model

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import java.util.*

/**
 * TB_IMP_CHK 实体类
 */
@Entity
@Table(name = "tb_imp_chk")
@IdClass(TbImpChkKey::class)
data class TbImpChk(
    // CHK_KIND
    @Column(name = "chk_kind")
    var chkKind: String? = null,


    // CHK_NAME
    @Column(name = "chk_name")
    var chkName: String? = null,


    // CHK_CONTENT
    @Id
    @Column(name = "chk_content")
    var chkContent: String? = null,


    // UPDT_DATE
    @Id
    @Column(name = "updt_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    var updtDate: Date? = null,


    // CHK_MOBILE
    @Column(name = "chk_mobile")
    var chkMobile: String? = null,


    // CHK_SENDTYPE
    @Column(name = "chk_sendtype")
    var chkSendtype: String? = null
)
