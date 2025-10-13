package com.wgzhao.addax.admin.dto

import lombok.Builder
import lombok.Getter
import lombok.Setter

@Builder
@Getter
@Setter
class ApiResponse<T> {
    private var code = 0
    private var message: String? = null
    private var data: T? = null

    constructor()

    constructor(code: Int, message: String?, data: T?) {
        this.code = code
        this.message = message
        this.data = data
    }

    override fun toString(): String {
        return "CommResponseDto{" +
                "code=" + code +
                ", message='" + message +
                '}'
    }

    companion object {
        // 静态方法构造统一返回结果
        fun <T> success(data: T?): ApiResponse<T?> {
            return ApiResponse<T?>(0, "success", data)
        }

        @JvmStatic
        fun <T> error(code: Int, message: String?): ApiResponse<T?> {
            return ApiResponse<T?>(code, message, null)
        }
    }
}
