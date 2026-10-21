package com.wgzhao.addax.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint
    implements AuthenticationEntryPoint
{

    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
        throws IOException
    {
        if (!"GET".equalsIgnoreCase(request.getMethod())
            || request.getRequestURI().startsWith("/api/v1/audit-events")) {
            auditRecorder.independentFailure("AUTHENTICATION_REQUIRED", "AUTH", "WARNING",
                "API", null, request.getRequestURI(), null, "UNAUTHORIZED", authException,
                Map.of("method", request.getMethod(), "path", request.getRequestURI()));
        }
        // 设置响应状态码和内容类型
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(APPLICATION_JSON_VALUE);

        // 构建统一的错误响应
        ApiResponse<Object> apiResponse = ApiResponse.error(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");

        // 写出 JSON 响应
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
