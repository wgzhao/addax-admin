package com.wgzhao.addax.admin.model

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.*
import org.hibernate.annotations.Formula
import java.util.*

/**
 * TB_IMP_ETL 实体类
 */
@Entity
@Table(name = "etl_table")
data class EtlTable(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "source_db", length = 32, nullable = false)
    var sourceDb: String? = null,

    @Column(name = "source_table", length = 64, nullable = false)
    var sourceTable: String? = null,

    @Column(name = "target_db", length = 50, nullable = false)
    var targetDb: String? = null,

    @Column(name = "target_table", length = 200, nullable = false)
    var targetTable: String? = null,

    @Column(name = "part_kind", length = 1)
    var partKind: String? = null,

    @Column(name = "part_name", length = 20)
    var partName: String? = null,

    @Column(name = "part_format", length = 10)
    var partFormat: String? = null,

    @Column(name = "filter", length = 2000, nullable = false)
    var filter: String? = null,

    @Column(name = "status", length = 1)
    var status: String? = null,

    @Column(name = "kind", length = 1)
    var kind: String? = null,

    @Column(name = "retry_cnt")
    var retryCnt: Int? = null,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "start_time")
    var startTime: Date? = null,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "end_time")
    var endTime: Date? = null,

    @Column(name = "max_runtime")
    var maxRuntime: Int? = null,

    @Column(name = "sid")
    var sid: Int? = null,

    @Column(name = "duration", nullable = false)
    var duration: Long? = null,

    @Column(name = "tbl_comment", length = 500)
    var tblComment: String? = null,

    @Formula("LOWER(concat_ws(',', source_db || '.' || source_table , target_db || '.' || target_table , part_kind , part_name , filter))")
    val filterColumn: String? = null
)
