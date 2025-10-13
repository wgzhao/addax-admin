package com.wgzhao.addax.admin.dto

@JvmRecord
data class VwImpEtlListDto(
    val tid: String?,
    val destOwner: String?,
    val sysName: String?,
    val souOwner: String?,
    val destTablename: String?,
    val flag: String?,
    val retryCnt: Int,
    val runtime: Int,
    val filterColumn: String?
)
