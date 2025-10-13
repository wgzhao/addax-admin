package com.wgzhao.addax.admin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.wgzhao.addax.admin.dto.ApiResponse.Companion.error
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import java.io.IOException

class CustomAuthenticationEntryPoint : AuthenticationEntryPoint {
    private val objectMapper = ObjectMapper()

    @Throws(IOException::class, ServletException::class)
    override fun commence(request: HttpServletRequest?, response: HttpServletResponse, authException: AuthenticationException) {
        // 设置响应状态码和内容类型
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        response.setContentType(MediaType.APPLICATION_JSON_VALUE)

        // 构建统一的错误响应
        val apiResponse = error<Any?>(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: " + authException.message)

        // 写出 JSON 响应
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse))
    }
}
