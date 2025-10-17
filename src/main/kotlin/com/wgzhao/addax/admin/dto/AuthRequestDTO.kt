package com.wgzhao.addax.admin.dto

class AuthRequestDTO {
    @JvmField
    var username: String? = null

    @JvmField
    var password: String? = null

    constructor()

    constructor(username: String?, password: String?) {
        this.username = username
        this.password = password
    }
}
