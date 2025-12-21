package com.wgzhao.addax.admin.exception;

import com.wgzhao.addax.admin.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 全局异常处理器。
 * 拦截并统一处理 ApiException 和其他未捕获异常，返回标准化的 API 响应。
 * 适用于 Spring Boot 控制器层，保证接口异常信息一致性。
 */
@ControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义 ApiException 异常。
     * 根据异常 code 映射为 HTTP 状态码，返回标准化错误响应。
     *
     * @param ex ApiException 异常对象
     * @return ResponseEntity 包含错误信息和对应 HTTP 状态码
     */
    @ExceptionHandler(ApiException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex)
    {
        HttpStatus status = switch (ex.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        log.warn("ApiException: code={}, message={}", ex.getCode(), ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error(ex.getCode(), ex.getMessage()), status);
    }

    /**
     * 处理所有未捕获的异常。
     * 返回 500 错误响应，记录异常日志。
     *
     * @param ex 未捕获的异常对象
     * @return ResponseEntity 包含错误信息和 500 状态码
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleOtherException(Exception ex)
    {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error(500, ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
