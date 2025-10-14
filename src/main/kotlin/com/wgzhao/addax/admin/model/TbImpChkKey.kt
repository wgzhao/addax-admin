package com.wgzhao.addax.admin.model

import jakarta.persistence.Embeddable

import java.io.Serializable
import java.util.*

/**
 * 采集校验主键实体类。
 * 用于标识校验内容和更新时间的复合主键。
 */
@Embeddable
class TbImpChkKey : Serializable {
    private val chkContent: String? = null
    private val updtDate: Date? = null

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as TbImpChkKey
        return chkContent == that.chkContent && updtDate == that.updtDate
    }

    override fun hashCode(): Int {
        return Objects.hash(chkContent, updtDate)
    }
}
