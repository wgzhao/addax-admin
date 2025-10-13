package com.wgzhao.addax.admin.dto

/**
 * 数据库连接信息传输对象
 */
data class DbConnectDto(
    val url: String? = null,
    val username: String? = null,
    val password: String? = null
)
