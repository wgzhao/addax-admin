package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reads the authoritative account state behind a JWT. The token is only a pointer: role, enabled
 * flag and auth_version are re-read here so a disabled account or a role change takes effect on the
 * next request instead of when the token expires.
 */
@Service
@RequiredArgsConstructor
public class UserAuthStateService
{
    private final JdbcTemplate jdbcTemplate;

    public record AuthState(String username, boolean enabled, int authVersion, String role)
    {
    }

    public AuthState load(String username)
    {
        List<AuthState> rows = jdbcTemplate.query(
            "select u.username, u.enabled, u.auth_version, a.authority " +
            "from users u left join authorities a on u.username = a.username " +
            "where u.username = ? order by a.authority",
            (rs, rowNum) -> new AuthState(
                rs.getString("username"),
                rs.getBoolean("enabled"),
                rs.getInt("auth_version"),
                Roles.normalize(rs.getString("authority"))),
            username);
        if (rows.isEmpty()) {
            return null;
        }
        AuthState first = rows.get(0);
        String role = Roles.highest(rows.stream().map(AuthState::role).toList());
        return new AuthState(first.username(), first.enabled(), first.authVersion(), role);
    }

    public void bumpAuthVersion(String username)
    {
        jdbcTemplate.update(
            "update users set auth_version = auth_version + 1 where username = ?",
            username);
    }
}
