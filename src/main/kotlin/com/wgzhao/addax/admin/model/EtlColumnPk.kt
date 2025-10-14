package com.wgzhao.addax.admin.model

import java.io.Serializable


class EtlColumnPk

    : Serializable {
    private val tid: Long = 0
    private val columnId = 0

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as EtlColumnPk
        return tid == that.tid && columnId == that.columnId
    }

    override fun hashCode(): Int {
        return java.lang.Long.hashCode(tid) * 31 + columnId
    }
}
