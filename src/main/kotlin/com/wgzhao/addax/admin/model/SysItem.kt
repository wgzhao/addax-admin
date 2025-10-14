package com.wgzhao.addax.admin.model

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*

@Table(name = "sys_item")
@Entity
@IdClass(SysItemPK::class)
data class SysItem(
    @Id
    @Column(name = "dict_code")
    @Schema(description = "字典编码，外键", example = "1000")
    var dictCode: Int,

    @Id
    @Column(name = "item_key", length = 255)
    @Schema(description = "字典项键", example = "SWITCH_TIME")
    var itemKey: String,

    @Column(name = "item_value", length = 2000)
    @Schema(description = "字典项值", example = "16:30")
    var itemValue: String,

    @Column(name = "remark", length = 4000)
    @Schema(description = "备注", example = "切日时间")
    var remark: String? = null
)
