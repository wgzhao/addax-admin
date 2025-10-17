package com.wgzhao.addax.admin.model

import jakarta.persistence.*

@Entity
@Table(name = "roles", schema = "stg01")
data class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null
)
