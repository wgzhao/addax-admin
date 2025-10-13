package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepo : JpaRepository<User?, Long?> {
    fun findByEmail(email: String?): User?

    fun findByUsername(username: String?): User?

    fun existsByUsername(username: String?): Boolean
}
