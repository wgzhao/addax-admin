package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.audit.AuditRequestFilter;
import com.wgzhao.addax.admin.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@AllArgsConstructor
public class JwtFilter
    extends OncePerRequestFilter
{

    private final JwtService jwtService;
    private final AuditRecorder auditRecorder;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    )
        throws ServletException, IOException
    {
        log.debug("doFilterInternal with jwtFilter");
        String token = jwtService.resolveToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = jwtService.parseTokenClaims(token);
        }
        catch (ExpiredJwtException ex) {
            log.debug("JWT token expired for request {}", request.getRequestURI());
            auditRecorder.independentFailure("AUTH_TOKEN_REJECTED", "AUTH", "WARNING",
                "API", null, request.getRequestURI(), null, "TOKEN_EXPIRED", ex,
                Map.of("reason", "expired"));
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"token 已过期\"}");
            return;
        }
        catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            auditRecorder.independentFailure("AUTH_TOKEN_REJECTED", "AUTH", "WARNING",
                "API", null, request.getRequestURI(), null, "TOKEN_INVALID", ex,
                Map.of("reason", "invalid"));
            filterChain.doFilter(request, response);
            return;
        }

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            auditRecorder.independentFailure("AUTH_TOKEN_REJECTED", "AUTH", "WARNING",
                "API", null, request.getRequestURI(), null, "TOKEN_SUBJECT_MISSING", null,
                Map.of("reason", "missing_subject"));
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("valid token, username: {}", username);
        request.setAttribute(AuditRequestFilter.JWT_ID_ATTRIBUTE, claims.getId());
        List<SimpleGrantedAuthority> authorities = jwtService.extractAuthorities(token).stream()
            .map((item) -> item == null ? "" : item.trim().toLowerCase())
            .filter((item) -> !item.isBlank())
            .map(SimpleGrantedAuthority::new)
            .toList();

        UsernamePasswordAuthenticationToken authenticationToken =
            new UsernamePasswordAuthenticationToken(username, null, authorities);

        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }
}
