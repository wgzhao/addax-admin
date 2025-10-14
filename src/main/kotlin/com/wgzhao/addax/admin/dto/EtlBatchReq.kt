package com.wgzhao.addax.admin.dto

data class EtlBatchReq (
    private var tids: MutableList<Long?>? = null,
    private var status: String? = null,
    private var retryCnt: Int = 3
)
