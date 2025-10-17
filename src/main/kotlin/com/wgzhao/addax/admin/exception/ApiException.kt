package com.wgzhao.addax.admin.exception

class ApiException(val code: Int, message: String?) : RuntimeException(message)