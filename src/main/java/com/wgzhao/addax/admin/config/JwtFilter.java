package com.wgzhao.addax.admin.config;

import com.wgzhao.addax.admin.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class JwtFilter
        extends OncePerRequestFilter
{

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    )
            throws ServletException, IOException
    {
        log.debug("doFilterInternal with jwtFilter");
        String username = null;
        String token = jwtService.resolveToken(request);
        if (token != null) {
            username = jwtService.extractUsername(token);
        }

        if (username == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // validate token is expired or not
        if (jwtService.isTokenExpired(token)) {
            log.debug("token expired, username: {}", username);
            // return 401 with a JSON message indicating the token is expired
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            // simple JSON body; keep it small to avoid dependencies
            response.getWriter().write("{\"message\":\"token 已过期\"}");
            return;
        }

        log.debug("valid token, username: {}", username);
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(username, null, List.of());

        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }
}