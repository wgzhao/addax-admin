package com.wgzhao.addax.admin.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*

@Entity
@Table(name = "sys_dict")
data class SysDict(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code")
    @Schema(description = "字典编码，主键", example = "1000")
    var code: Int,

    @Column(name = "name")
    @Schema(description = "字典名称", example = "系统参数")
    var name: String? = null,

    @Column(name = "classification")
    @Schema(description = "字典分类", example = "system")
    var classification: String? = null,

    @Column(name = "remark")
    @Schema(description = "备注", example = "系统参数相关字典")
    var remark: String? = null
)
