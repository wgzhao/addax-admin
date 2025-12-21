package com.wgzhao.addax.admin.dto;

public record ApiResponse<T>(int code, String message, T data)
{

    // 静态方法构造统一返回结果
    public static <T> ApiResponse<T> success(T data)
    {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message)
    {
        return new ApiResponse<>(code, message, null);
    }
}
