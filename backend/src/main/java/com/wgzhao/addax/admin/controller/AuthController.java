package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.dto.AuthRequestDTO;
import com.wgzhao.addax.admin.dto.ChangePasswordDTO;
import com.wgzhao.addax.admin.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
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
public class AuthController
{

    /**
     * JWT服务
     */
    private final JwtService jwtService;
    /**
     * Spring Security认证管理器
     */
    private final AuthenticationManager authenticationManager;

    // 注入用于更新密码的 manager 与编码器
    private final JdbcUserDetailsManager jdbcUserDetailsManager;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录认证，返回JWT令牌
     *
     * @param authRequestDTO 登录请求参数
     * @return JWT 令牌或认证失败信息
     */
    @PostMapping("/login")
    public ApiResponse<String> AuthenticateAndGetToken(@RequestBody AuthRequestDTO authRequestDTO)
    {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authRequestDTO.username(), authRequestDTO.password()));
            if (authentication.isAuthenticated()) {
                return ApiResponse.success(jwtService.generateToken(authRequestDTO.username()));
            }
            else {
                return ApiResponse.error(401, "账号或密码不正确");
            }
        }
        catch (org.springframework.security.core.AuthenticationException ex) {
            // 认证失败（如密码错误或用户不存在）——返回友好消息，而不是让异常传播导致 500
            log.debug("authentication failed for user {}: {}", authRequestDTO.username(), ex.getMessage());
            return ApiResponse.error(401, "账号或密码不正确");
        }
        catch (Exception ex) {
            log.error("unexpected error during authentication", ex);
            return ApiResponse.error(500, "internal error");
        }
    }

    /**
     * 修改当前登录用户的密码
     * 请求体: { "currentPassword": "old", "newPassword": "new" }
     */
    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(@RequestBody ChangePasswordDTO dto, HttpServletRequest request)
    {
        String username = SecurityContextHolder.getContext().getAuthentication() == null
            ? null
            : SecurityContextHolder.getContext().getAuthentication().getName();

        if (username == null) {
            return ApiResponse.error(401, "unauthenticated");
        }

        // 先验证旧密码是否正确（通过 AuthenticationManager）
        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, dto.currentPassword())
            );
            if (!auth.isAuthenticated()) {
                return ApiResponse.error(403, "current password is incorrect");
            }
        }
        catch (Exception ex) {
            log.debug("Failed to authenticate current password for user {}", username);
            return ApiResponse.error(403, "current password is incorrect");
        }

        // 校验新密码强度（最小长度示例）
        if (dto.newPassword() == null || dto.newPassword().length() < 6) {
            return ApiResponse.error(400, "new password must be at least 6 characters");
        }

        // 更新密码（使用编码后的密码）
        try {
            var userDetails = jdbcUserDetailsManager.loadUserByUsername(username);
            String encoded = passwordEncoder.encode(dto.newPassword());
            // construct a new UserDetails preserving authorities and flags but with new password
            var updated = org.springframework.security.core.userdetails.User.withUserDetails(userDetails)
                .password(encoded)
                .build();
            jdbcUserDetailsManager.updateUser(updated);
            // 在更新后，重新刷新 SecurityContext 中的认证信息为最新（可选）
            SecurityContextHolder.clearContext();
            return ApiResponse.success("password changed");
        }
        catch (Exception ex) {
            log.error("Failed to update password for user {}", username, ex);
            return ApiResponse.error(500, "failed to update password");
        }
    }
}
