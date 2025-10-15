package com.wgzhao.addax.admin.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T?, message: String? = "success", code: Int = 0): ApiResponse<T> =
            ApiResponse(code = code, message = message, data = data)

        fun <T> error(code: Int, message: String? = "error"): ApiResponse<T> =
            ApiResponse(code = code, message = message, data = null)
    }
}
