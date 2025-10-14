package com.wgzhao.addax.admin.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "etl_job")
data class EtlJob(
    @Id
    val tid: Long = 0,
    val job: String? = null
)
