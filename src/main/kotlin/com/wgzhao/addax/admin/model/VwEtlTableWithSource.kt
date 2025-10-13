package com.wgzhao.addax.admin.model

import com.fasterxml.jackson.annotation.JsonFormat
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Formula
import java.time.LocalDateTime

/**
 * 映射 vw_etl_table_with_source 视图
 */
@Entity
@Table(name = "vw_etl_table_with_source")
data class VwEtlTableWithSource(
    @Id
    val id: Long? = null,
    val sourceDb: String? = null,
    val sourceTable: String? = null,
    val targetDb: String? = null,
    val targetTable: String? = null,
    val partKind: String? = null,
    val partName: String? = null,
    val partFormat: String? = null,
    val storageFormat: String? = null,
    val compressFormat: String? = null,
    val filter: String? = null,
    val status: String? = null,
    val kind: String? = null,
    val retryCnt: Int? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val startTime: LocalDateTime? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val endTime: LocalDateTime? = null,
    val sid: Int? = null,
    val duration: Int? = null,
    val code: String? = null,
    val name: String? = null,
    val url: String? = null,
    val username: String? = null,
    val pass: String? = null,
    val startAt: String? = null,
    val enabled: Boolean? = null,
    val tblComment: String? = null,
    @Formula("UPPER(concat_ws('|', source_db || '.' || source_table , target_db || '.' || target_table , part_kind , part_name , code, name))")
    val filterColumn: String? = null
)
