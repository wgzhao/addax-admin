package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.dto.CreateUserDTO;
import com.wgzhao.addax.admin.dto.UpdateUserDTO;
import com.wgzhao.addax.admin.dto.UserAdminDto;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService
{
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public List<UserAdminDto> listUsers()
    {
        requireAdmin();
        List<String> usernames = jdbcTemplate.query(
            "select username from users order by username",
            (rs, rowNum) -> rs.getString("username")
        );

        return usernames.stream()
            .map(this::toUserAdminDto)
            .toList();
    }

    public UserAdminDto currentUser()
    {
        String username = currentUsername();
        if (username == null || username.isBlank()) {
            throw new ApiException(404, "当前登录账号不存在");
        }
        return toUserAdminDto(username);
    }

    @Transactional
    public UserAdminDto createUser(CreateUserDTO dto)
    {
        requireAdmin();
        String username = normalizeUsername(dto.username());
        String password = dto.password();
        if (password == null || password.length() < 6) {
            throw new ApiException(400, "密码至少需要 6 位");
        }

        JdbcUserDetailsManager manager = getUserManager();
        if (manager.userExists(username)) {
            throw new ApiException(409, "账号已存在");
        }

        boolean enabled = dto.enabled() == null || dto.enabled();
        String authority = normalizeAuthority(dto.authority());

        var user = org.springframework.security.core.userdetails.User.withUsername(username)
            .password(passwordEncoder.encode(password))
            .authorities(authority)
            .disabled(!enabled)
            .build();

        manager.createUser(user);
        return toUserAdminDto(username);
    }

    @Transactional
    public UserAdminDto updateUser(String username, UpdateUserDTO dto)
    {
        String normalizedUsername = normalizeUsername(username);
        boolean admin = isCurrentUserAdmin();
        String currentUsername = currentUsername();
        boolean self = normalizedUsername.equals(currentUsername);
        if (!admin && !self) {
            throw new ApiException(403, "仅管理员可修改其他账号");
        }
        if (!admin && dto.authority() != null && !dto.authority().isBlank()) {
            throw new ApiException(403, "仅管理员可修改角色");
        }

        JdbcUserDetailsManager manager = getUserManager();

        if (!manager.userExists(normalizedUsername)) {
            throw new ApiException(404, "账号不存在");
        }

        UserDetails current = manager.loadUserByUsername(normalizedUsername);
        var builder = org.springframework.security.core.userdetails.User.withUserDetails(current);

        if (dto.password() != null && !dto.password().isBlank()) {
            if (dto.password().length() < 6) {
                throw new ApiException(400, "密码至少需要 6 位");
            }
            builder.password(passwordEncoder.encode(dto.password()));
        }

        manager.updateUser(builder.build());

        // 显式覆盖权限，避免不同 JdbcUserDetailsManager 配置导致角色未更新
        if (dto.authority() != null && !dto.authority().isBlank()) {
            String normalizedAuthority = normalizeAuthority(dto.authority());
            jdbcTemplate.update("delete from authorities where username = ?", normalizedUsername);
            jdbcTemplate.update(
                "insert into authorities(username, authority) values (?, ?)",
                normalizedUsername,
                normalizedAuthority
            );
        }

        return toUserAdminDto(normalizedUsername);
    }

    @Transactional
    public void deleteUser(String username)
    {
        requireAdmin();
        String normalizedUsername = normalizeUsername(username);
        String currentUsername = currentUsername();
        if (normalizedUsername.equals(currentUsername)) {
            throw new ApiException(400, "不能删除当前登录账号");
        }

        JdbcUserDetailsManager manager = getUserManager();
        if (!manager.userExists(normalizedUsername)) {
            throw new ApiException(404, "账号不存在");
        }

        manager.deleteUser(normalizedUsername);
    }

    @Transactional
    public UserAdminDto enableUser(String username)
    {
        requireAdmin();
        return setUserEnabled(username, true);
    }

    @Transactional
    public UserAdminDto disableUser(String username)
    {
        requireAdmin();
        return setUserEnabled(username, false);
    }

    private UserAdminDto setUserEnabled(String username, boolean enabled)
    {
        String normalizedUsername = normalizeUsername(username);
        String currentUsername = currentUsername();
        if (!enabled && normalizedUsername.equals(currentUsername)) {
            throw new ApiException(400, "不能禁用当前登录账号");
        }

        JdbcUserDetailsManager manager = getUserManager();
        if (!manager.userExists(normalizedUsername)) {
            throw new ApiException(404, "账号不存在");
        }

        UserDetails current = manager.loadUserByUsername(normalizedUsername);
        var updated = org.springframework.security.core.userdetails.User.withUserDetails(current)
            .disabled(!enabled)
            .build();

        manager.updateUser(updated);
        return toUserAdminDto(normalizedUsername);
    }

    private UserAdminDto toUserAdminDto(String username)
    {
        JdbcUserDetailsManager manager = getUserManager();
        UserDetails user = manager.loadUserByUsername(username);

        List<String> authorities = jdbcTemplate.query(
            "select authority from authorities where username = ? order by authority",
            (rs, rowNum) -> normalizeAuthority(rs.getString("authority")),
            username
        );

        return new UserAdminDto(user.getUsername(), user.isEnabled(), authorities);
    }

    private JdbcUserDetailsManager getUserManager()
    {
        if (!(userDetailsService instanceof JdbcUserDetailsManager manager)) {
            throw new ApiException(500, "当前用户服务不支持账号管理");
        }
        return manager;
    }

    private String normalizeUsername(String username)
    {
        if (username == null || username.isBlank()) {
            throw new ApiException(400, "用户名不能为空");
        }
        return username.trim();
    }

    private String normalizeAuthority(String authority)
    {
        if (authority == null || authority.isBlank()) {
            return "user";
        }

        String normalized = authority.trim().toLowerCase();
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring(5);
        }

        if ("admin".equals(normalized)) {
            return "admin";
        }
        return "user";
    }

    private String currentUsername()
    {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : String.valueOf(auth.getName());
    }

    private boolean isCurrentUserAdmin()
    {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            boolean byToken = auth.getAuthorities().stream()
                .map((a) -> normalizeAuthority(a.getAuthority()))
                .anyMatch("admin"::equals);
            if (byToken) {
                return true;
            }
        }

        String current = currentUsername();
        if (current == null || current.isBlank()) {
            return false;
        }

        List<String> roles = jdbcTemplate.query(
            "select authority from authorities where username = ?",
            (rs, rowNum) -> normalizeAuthority(rs.getString("authority")),
            current
        );
        return roles.stream().anyMatch("admin"::equals);
    }

    private void requireAdmin()
    {
        if (!isCurrentUserAdmin()) {
            throw new ApiException(403, "仅管理员可执行该操作");
        }
    }
}
