package com.wgzhao.addax.admin.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditRequestFilter
    extends OncePerRequestFilter
{
    public static final String REQUEST_ID_ATTRIBUTE = AuditRequestFilter.class.getName() + ".requestId";
    public static final String CLIENT_IP_ATTRIBUTE = AuditRequestFilter.class.getName() + ".clientIp";
    public static final String JWT_ID_ATTRIBUTE = AuditRequestFilter.class.getName() + ".jwtId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Value("${audit.trust-forwarded-headers:false}")
    private boolean trustForwardedHeaders;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    )
        throws ServletException, IOException
    {
        String requestId = UUID.randomUUID().toString();
        String clientIp = resolveClientIp(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(CLIENT_IP_ATTRIBUTE, clientIp);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            MDC.remove("requestId");
        }
    }

    private String resolveClientIp(HttpServletRequest request)
    {
        if (trustForwardedHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
