package com.wgzhao.addax.admin.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "TableMetaDto", description = "数据库表元信息（仅名称与注释）")
data class TableMetaDto(
    @Schema(description = "表名")
    val name: String,

    @Schema(description = "表注释")
    val comment: String? = null
)
