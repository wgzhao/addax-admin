package com.wgzhao.addax.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class CustomAccessDeniedHandler
    implements AccessDeniedHandler
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
        throws IOException
    {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(APPLICATION_JSON_VALUE);
        ApiResponse<Object> apiResponse = ApiResponse.error(HttpServletResponse.SC_FORBIDDEN, "Forbidden: " + accessDeniedException.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}

