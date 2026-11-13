package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.Roles;
import com.wgzhao.addax.admin.dto.SourceGrantDto;
import com.wgzhao.addax.admin.dto.SourceGrantInput;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persistence and validation for source grants. Grants are the only source-scoped authorization
 * data; keeping them in one service makes the cache in {@link AuthzService} unambiguous.
 */
@Service
@RequiredArgsConstructor
public class UserSourceGrantService
{
    public enum AccessLevel
    {
        VIEW,
        OPERATE
    }

    private final JdbcTemplate jdbcTemplate;

    public Map<Integer, AccessLevel> load(String username)
    {
        if (username == null || username.isBlank()) {
            return Map.of();
        }
        Map<Integer, AccessLevel> grants = new LinkedHashMap<>();
        // VIEW rows from the earlier model are ignored: a user already reads every source, so only
        // OPERATE changes what the account may do.
        jdbcTemplate.query(
            "select source_id, access_level from public.user_source_grant " +
            "where username = ? and access_level = 'OPERATE'",
            rs -> {
                grants.put(rs.getInt("source_id"), parseLevel(rs.getString("access_level")));
            },
            username);
        return grants;
    }

    public List<SourceGrantDto> list(String username)
    {
        return jdbcTemplate.query(
            "select g.source_id, s.name, g.access_level " +
            "from public.user_source_grant g join public.etl_source s on s.id = g.source_id " +
            "where g.username = ? and g.access_level = 'OPERATE' order by s.name, g.source_id",
            (rs, rowNum) -> new SourceGrantDto(
                rs.getInt("source_id"),
                rs.getString("name"),
                rs.getString("access_level")),
            username);
    }

    @Transactional
    public List<SourceGrantDto> replace(String username, List<SourceGrantInput> inputs, String grantedBy)
    {
        if (!userExists(username)) {
            throw new ApiException(404, "账号不存在");
        }
        if (!isUserRole(username)) {
            throw new ApiException(400, "只有 user 角色可以分配采集源授权");
        }
        Map<Integer, AccessLevel> normalized = normalize(inputs);
        if (!normalized.isEmpty()) {
            requireSourcesExist(normalized.keySet());
        }
        jdbcTemplate.update("delete from public.user_source_grant where username = ?", username);
        for (Map.Entry<Integer, AccessLevel> entry : normalized.entrySet()) {
            jdbcTemplate.update(
                "insert into public.user_source_grant " +
                "(username, source_id, access_level, granted_by) values (?, ?, ?, ?)",
                username,
                entry.getKey(),
                entry.getValue().name(),
                grantedBy == null || grantedBy.isBlank() ? "system" : grantedBy);
        }
        return list(username);
    }

    private Map<Integer, AccessLevel> normalize(List<SourceGrantInput> inputs)
    {
        Map<Integer, AccessLevel> normalized = new LinkedHashMap<>();
        if (inputs == null) {
            return normalized;
        }
        for (SourceGrantInput input : inputs) {
            if (input == null) {
                continue;
            }
            if (input.sourceId() <= 0) {
                throw new ApiException(400, "采集源 ID 无效");
            }
            normalized.put(input.sourceId(), parseInputLevel(input.accessLevel()));
        }
        return normalized;
    }

    /**
     * Grants are management rights only. A user already reads every source, so VIEW is not a
     * meaningful grant level anymore; reject it instead of storing a no-op row.
     */
    private AccessLevel parseInputLevel(String value)
    {
        if (value == null || value.isBlank()) {
            throw new ApiException(400, "授权级别只支持 OPERATE");
        }
        try {
            AccessLevel level = AccessLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (level != AccessLevel.OPERATE) {
                throw new ApiException(400, "授权级别只支持 OPERATE");
            }
            return level;
        }
        catch (IllegalArgumentException e) {
            throw new ApiException(400, "授权级别只支持 OPERATE");
        }
    }

    private AccessLevel parseLevel(String value)
    {
        if (value == null || value.isBlank()) {
            return AccessLevel.OPERATE;
        }
        try {
            return AccessLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return AccessLevel.OPERATE;
        }
    }

    /**
     * Guest is read-only by definition and admin already has every source; allowing either to
     * receive grants would make the role meaningless. The effective role (highest authority) is
     * used, so a legacy admin+user pair still cannot receive grants.
     */
    private boolean isUserRole(String username)
    {
        List<String> authorities = jdbcTemplate.query(
            "select authority from public.authorities where username = ?",
            (rs, rowNum) -> rs.getString("authority"),
            username);
        return Roles.USER.equals(Roles.highest(authorities));
    }

    public void deleteAll(String username)
    {
        jdbcTemplate.update("delete from public.user_source_grant where username = ?", username);
    }

    private boolean userExists(String username)
    {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from public.users where username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    private void requireSourcesExist(Collection<Integer> sourceIds)
    {
        String placeholders = String.join(",", java.util.Collections.nCopies(sourceIds.size(), "?"));
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from public.etl_source where id in (" + placeholders + ")",
            Integer.class,
            sourceIds.toArray());
        if (count == null || count != sourceIds.size()) {
            throw new ApiException(400, "授权列表包含不存在的采集源");
        }
    }
}
