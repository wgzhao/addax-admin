package com.wgzhao.addax.admin.exception;

import com.wgzhao.addax.admin.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        log.warn("ApiException: code={}, message={}", ex.getCode(), ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error(ex.getCode(), ex.getMessage()), status);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> handleOtherException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(ApiResponse.error(500, ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
