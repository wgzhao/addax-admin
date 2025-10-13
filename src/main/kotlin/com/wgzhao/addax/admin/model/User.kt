package com.wgzhao.addax.admin.model

import jakarta.persistence.*

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["username"]),
        UniqueConstraint(columnNames = ["email"]),
        UniqueConstraint(columnNames = ["phone"])
    ],
    schema = "stg01"
)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var username: String? = null,
    var name: String? = null,
    var password: String? = null,
    var email: String? = null,
    var phone: String? = null,
    var enabled: Boolean? = null,

    @ManyToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinTable(name = "user_roles", schema = "stg01", joinColumns = [JoinColumn(name = "user_id")], inverseJoinColumns = [JoinColumn(name = "role_id")])
    var roles: MutableCollection<Role?>? = null
)
