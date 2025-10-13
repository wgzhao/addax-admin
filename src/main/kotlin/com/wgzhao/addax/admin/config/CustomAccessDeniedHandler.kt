package com.wgzhao.addax.admin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.wgzhao.addax.admin.dto.ApiResponse.Companion.error
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import java.io.IOException

class CustomAccessDeniedHandler : AccessDeniedHandler {
    private val objectMapper = ObjectMapper()

    @Throws(IOException::class, ServletException::class)
    override fun handle(request: HttpServletRequest?, response: HttpServletResponse, accessDeniedException: AccessDeniedException) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN)
        response.setContentType(MediaType.APPLICATION_JSON_VALUE)
        val apiResponse = error<Any?>(HttpServletResponse.SC_FORBIDDEN, "Forbidden: " + accessDeniedException.message)
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse))
    }
}

