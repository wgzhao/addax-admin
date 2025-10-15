package com.wgzhao.addax.admin.model

import jakarta.persistence.Embeddable

import java.util.*

/**
 * 采集校验主键实体类。
 * 用于标识校验内容和更新时间的复合主键。
 */
@Embeddable
data class TbImpChkKey(
    private val chkContent: String? = null,
    private val updtDate: Date? = null
)

