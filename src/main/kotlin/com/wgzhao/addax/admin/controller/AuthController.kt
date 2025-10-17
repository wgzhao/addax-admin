package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.dto.ApiResponse
import com.wgzhao.addax.admin.dto.AuthRequestDTO
import com.wgzhao.addax.admin.service.JwtService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.*
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 用户认证接口，提供登录认证功能
 */
@RestController
@CrossOrigin
@RequestMapping("/auth")
class AuthController(
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager
) {
    private val log = KotlinLogging.logger {}

    /**
     * 用户登录认证，返回JWT令牌
     * @param authRequestDTO 登录请求参数
     * @return JWT令牌或认证失败信息
     */
    @PostMapping("/login")
    fun authenticateAndGetToken(@RequestBody authRequestDTO: AuthRequestDTO): ApiResponse<String> {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(authRequestDTO.username, authRequestDTO.password)
        )
        return if (authentication.isAuthenticated) {
            ApiResponse.success(jwtService.generateToken(authRequestDTO.username))
        } else {
            ApiResponse.error(401, "failed to authenticate user")
        }
    }
}
