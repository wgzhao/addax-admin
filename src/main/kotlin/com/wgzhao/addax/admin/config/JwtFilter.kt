package com.wgzhao.addax.admin.config

import com.wgzhao.addax.admin.service.JwtService
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class JwtFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(JwtFilter::class.java)
    @Autowired
    private val jwtService: JwtService? = null

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        log.debug("doFilterInternal with jwtFilter")
        var username: String? = null
        val token = jwtService!!.resolveToken(request)
        if (token != null) {
            username = jwtService.extractUsername(token)
        }

        if (username == null) {
            filterChain.doFilter(request, response)
            return
        }
        // validate token is expired or not
        if (jwtService.isTokenExpired(token)) {
            log.debug("token expired, username: {}", username)
            filterChain.doFilter(request, response)
            return
        }

        log.debug("valid token, username: {}", username)
        val authenticationToken =
            UsernamePasswordAuthenticationToken(username, null, mutableListOf<GrantedAuthority?>())

        authenticationToken.setDetails(WebAuthenticationDetailsSource().buildDetails(request))
        SecurityContextHolder.getContext().setAuthentication(authenticationToken)
        filterChain.doFilter(request, response)
    }
}