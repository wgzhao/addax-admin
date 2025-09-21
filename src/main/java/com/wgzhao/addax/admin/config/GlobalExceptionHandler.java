//package com.wgzhao.addax.admin.config;
//
//import com.wgzhao.addax.admin.dto.ApiResponse;
//import org.springframework.http.HttpStatus;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.ResponseStatus;
//import org.springframework.web.bind.annotation.RestControllerAdvice;
//
//@RestControllerAdvice
//public class GlobalExceptionHandler {
//
//    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
//    @ExceptionHandler(Exception.class)
//    public ApiResponse<Void> handleException(Exception ex) {
//        return ApiResponse.error(500, "Internal Server Error");
//    }
//}
