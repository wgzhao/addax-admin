package com.wgzhao.addax.admin.exception;

import com.wgzhao.addax.admin.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * 拦截并统一处理 ApiException 和其他未捕获异常，返回标准化的 API 响应。
 */
@ControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex)
    {
        HttpStatus status = switch (ex.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        log.warn("ApiException: code={}, message={}", ex.getCode(), ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error(ex.getCode(), ex.getMessage()), status);
    }

    /**
     * Bean Validation 校验失败 (@Valid / @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex)
    {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        log.debug("Validation failed: {}", message);
        return new ResponseEntity<>(ApiResponse.error(400, message), HttpStatus.BAD_REQUEST);
    }

    /**
     * 请求体格式错误（JSON 解析失败）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleUnreadableException(HttpMessageNotReadableException ex)
    {
        log.debug("HTTP message not readable: {}", ex.getMessage());
        return new ResponseEntity<>(ApiResponse.error(400, "请求体格式错误"), HttpStatus.BAD_REQUEST);
    }

    /**
     * 兜底：返回通用错误消息，不暴露异常细节
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleOtherException(Exception ex)
    {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error(500, "Internal server error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
