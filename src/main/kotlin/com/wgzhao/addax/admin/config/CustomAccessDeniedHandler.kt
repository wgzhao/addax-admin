package com.wgzhao.addax.admin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.wgzhao.addax.admin.dto.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler

class CustomAccessDeniedHandler : AccessDeniedHandler {

    private val objectMapper = ObjectMapper()

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val apiResponse =
            ApiResponse.error<String>(HttpServletResponse.SC_FORBIDDEN, "Forbidden: ${accessDeniedException.message}")
        response.writer.write(objectMapper.writeValueAsString(apiResponse))
    }
}
