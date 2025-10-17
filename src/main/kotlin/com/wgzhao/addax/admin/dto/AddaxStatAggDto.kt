package com.wgzhao.addax.admin.dto

data class AddaxStatAggDto(
    val name: String? = null,
    val runDates: MutableList<String?>? = null,
    val takeSecs: MutableList<Long?>? = null
)
