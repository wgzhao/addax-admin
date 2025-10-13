package com.wgzhao.addax.admin.exception

import lombok.Getter

@Getter
class ApiException(private val code: Int, message: String?) : RuntimeException(message) 