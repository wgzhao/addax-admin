package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.AuthRequestDTO;
import com.wgzhao.addax.admin.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口，提供登录认证功能
 */
@RestController
@CrossOrigin
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    /** JWT服务 */
    private final JwtService jwtService;
    /** Spring Security认证管理器 */
    private final AuthenticationManager authenticationManager;

    /**
     * 用户登录认证，返回JWT令牌
     * @param authRequestDTO 登录请求参数
     * @return JWT令牌或认证失败信息
     */
    @PostMapping("/login")
    public ApiResponse<String> AuthenticateAndGetToken(@RequestBody AuthRequestDTO authRequestDTO){
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authRequestDTO.username(), authRequestDTO.password()));
        if(authentication.isAuthenticated()){
            return ApiResponse.success(jwtService.generateToken(authRequestDTO.username()));
        } else {
            return ApiResponse.error(401, "failed to authenticate user");
        }
    }
}
