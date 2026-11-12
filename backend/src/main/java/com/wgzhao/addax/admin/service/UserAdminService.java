package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Roles;
import com.wgzhao.addax.admin.dto.CreateUserDTO;
import com.wgzhao.addax.admin.dto.CurrentUserDto;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAdminService
{
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthStateService userAuthStateService;
    private final UserSourceGrantService userSourceGrantService;
    private final AuthzService authzService;
    private final JdbcTemplate jdbcTemplate;

    public List<UserAdminDto> listUsers()
    {
        requireAdmin();
        // Single JOIN query replacing the previous N+1 pattern (1 query for usernames + N×2 per-user queries)
        Map<String, UserAdminDto> result = new java.util.LinkedHashMap<>();
        jdbcTemplate.query(
            "SELECT u.username, u.display_name, u.email, u.remark, u.enabled, " +
            "u.created_at, u.last_login_at, u.password_changed_at, a.authority " +
            "FROM users u LEFT JOIN authorities a ON u.username = a.username " +
            "ORDER BY u.username, a.authority",
            rs -> {
                String username = rs.getString("username");
                String rawAuthority = rs.getString("authority");
                String authority = rawAuthority != null ? normalizeAuthority(rawAuthority) : null;
                String displayName = rs.getString("display_name");
                String email = rs.getString("email");
                String remark = rs.getString("remark");
                boolean enabled = rs.getBoolean("enabled");
                LocalDateTime createdAt = toLocalDateTime(rs.getTimestamp("created_at"));
                LocalDateTime lastLoginAt = toLocalDateTime(rs.getTimestamp("last_login_at"));
                LocalDateTime passwordChangedAt = toLocalDateTime(rs.getTimestamp("password_changed_at"));

                UserAdminDto dto = result.computeIfAbsent(username,
                    k -> new UserAdminDto(
                        k,
                        displayName,
                        email,
                        remark,
                        enabled,
                        new java.util.ArrayList<>(),
                        createdAt,
                        lastLoginAt,
                        passwordChangedAt));
                if (authority != null && !authority.isBlank()) {
                    dto.authorities().add(authority);
                }
            }
        );
        return new java.util.ArrayList<>(result.values());
    }

    public CurrentUserDto currentUser()
    {
        String username = currentUsername();
        if (username == null || username.isBlank()) {
            throw new ApiException(404, "当前登录账号不存在");
        }
        UserAdminDto user = toUserAdminDto(username);
        return new CurrentUserDto(
            user.username(),
            user.displayName(),
            user.email(),
            user.remark(),
            user.enabled(),
            user.authorities(),
            userSourceGrantService.list(username),
            user.createdAt(),
            user.lastLoginAt(),
            user.passwordChangedAt());
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
        String displayName = normalizeDisplayName(dto.displayName(), username);
        String email = blankToNull(dto.email());
        String remark = blankToNull(dto.remark());
        validateProfile(displayName, email, remark);
        jdbcTemplate.update(
            "update users set display_name = ?, email = ?, remark = ?, password_changed_at = now() " +
            "where username = ?",
            displayName, email, remark, username);
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

        UserAdminDto beforeProfile = toUserAdminDto(normalizedUsername);
        UserDetails current = manager.loadUserByUsername(normalizedUsername);
        var builder = org.springframework.security.core.userdetails.User.withUserDetails(current);

        if (dto.password() != null && !dto.password().isBlank()) {
            if (dto.password().length() < 6) {
                throw new ApiException(400, "密码至少需要 6 位");
            }
            builder.password(passwordEncoder.encode(dto.password()));
        }

        manager.updateUser(builder.build());

        String displayName = dto.displayName() == null
            ? beforeProfile.displayName()
            : normalizeDisplayName(dto.displayName(), normalizedUsername);
        String email = dto.email() == null ? beforeProfile.email() : blankToNull(dto.email());
        String remark = dto.remark() == null ? beforeProfile.remark() : blankToNull(dto.remark());
        validateProfile(displayName, email, remark);
        jdbcTemplate.update(
            "update users set display_name = ?, email = ?, remark = ? where username = ?",
            displayName, email, remark, normalizedUsername);
        if (dto.password() != null && !dto.password().isBlank()) {
            userAuthStateService.markPasswordChanged(normalizedUsername);
        }

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

        UserAdminDto updated = toUserAdminDto(normalizedUsername);
        // Grants only mean something for the user role. Clearing them on a role change prevents a
        // later change back to user from silently reactivating the old management rights.
        if (!Roles.USER.equals(Roles.highest(updated.authorities()))) {
            userSourceGrantService.deleteAll(normalizedUsername);
        }
        // Any account edit invalidates existing tokens: a changed password, role or enabled flag must
        // take effect immediately instead of waiting for the eight-day JWT to expire.
        userAuthStateService.bumpAuthVersion(normalizedUsername);
        authzService.invalidateGrants(normalizedUsername);
        return updated;
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
        userAuthStateService.bumpAuthVersion(normalizedUsername);
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
        Map<String, Object> profile = jdbcTemplate.queryForMap(
            "select display_name, email, remark, created_at, last_login_at, password_changed_at " +
            "from users where username = ?",
            username);

        return new UserAdminDto(
            user.getUsername(),
            (String) profile.get("display_name"),
            (String) profile.get("email"),
            (String) profile.get("remark"),
            user.isEnabled(),
            authorities,
            toLocalDateTime(profile.get("created_at")),
            toLocalDateTime(profile.get("last_login_at")),
            toLocalDateTime(profile.get("password_changed_at")));
    }

    private String normalizeDisplayName(String value, String username)
    {
        return value == null || value.isBlank() ? username : value.trim();
    }

    private String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateProfile(String displayName, String email, String remark)
    {
        if (displayName != null && displayName.length() > 100) {
            throw new ApiException(400, "显示名不能超过 100 个字符");
        }
        if (email != null && email.length() > 200) {
            throw new ApiException(400, "邮箱不能超过 200 个字符");
        }
        if (remark != null && remark.length() > 500) {
            throw new ApiException(400, "备注不能超过 500 个字符");
        }
    }

    private LocalDateTime toLocalDateTime(Object value)
    {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return null;
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
        return Roles.normalize(authority);
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
