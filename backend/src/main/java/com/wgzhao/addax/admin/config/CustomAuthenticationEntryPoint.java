package com.wgzhao.addax.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.dto.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class CustomAuthenticationEntryPoint
    implements AuthenticationEntryPoint
{

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
        throws IOException, ServletException
    {
        // 设置响应状态码和内容类型
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(APPLICATION_JSON_VALUE);

        // 构建统一的错误响应
        ApiResponse<Object> apiResponse = ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: " + authException.getMessage());

        // 写出 JSON 响应
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
