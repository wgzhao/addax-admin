package com.wgzhao.addax.admin.model

import jakarta.persistence.*
import java.sql.Timestamp

/**
 * 采集表字段信息实体类。
 * 用于描述采集表的字段结构、类型、注释等元数据信息。
 */
@Entity
@Table(name = "etl_column")
@IdClass(EtlColumnPk::class)
data class EtlColumn(
    @Id
    @Column(name = "tid", nullable = false)
    var tid: Long,

    @Column(name = "column_name", length = 255)
    var columnName: String,

    @Id
    @Column(name = "column_id")
    var columnId: Int = 0,

    @Column(name = "source_type", length = 64)
    var sourceType: String,

    @Column(name = "data_length")
    var dataLength: Int = 0,

    @Column(name = "data_precision")
    var dataPrecision: Int? = null,

    @Column(name = "data_scale")
    var dataScale: Int? = null,

    @Column(name = "col_comment", length = 4000)
    var colComment: String? = null,

    @Column(name = "target_type", length = 50, nullable = false)
    var targetType: String,

    @Column(name = "target_type_full", length = 100)
    var targetTypeFull: String,

    @Column(name = "update_at")
    var updateAt: Timestamp
)
