package com.wgzhao.addax.admin.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AuditRepository
{
    private static final String INSERT_SQL = """
        insert into public.audit_event (
            event_id, occurred_at, request_id, actor_type, actor_username, actor_roles, session_id,
            action, category, severity, resource_type, resource_id, resource_name, outcome,
            http_method, request_path, client_ip, user_agent, node_id, before_data, after_data,
            changed_fields, error_code, error_message, metadata
        ) values (
            ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as inet), ?,
            ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), ?, ?, cast(? as jsonb)
        )
        returning id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditSanitizer sanitizer;

    public long insert(AuditEventData event)
    {
        JsonNode before = sanitizer.sanitize(event.beforeData());
        JsonNode after = sanitizer.sanitize(event.afterData());
        JsonNode changedFields = sanitizer.changedFields(before, after);
        Long id = jdbcTemplate.queryForObject(
            INSERT_SQL,
            Long.class,
            event.eventId(),
            event.occurredAt(),
            event.requestId(),
            event.actorType(),
            trimTo(event.actorUsername(), 100),
            json(event.actorRoles()),
            trimTo(event.sessionId(), 64),
            trimTo(event.action(), 80),
            trimTo(event.category(), 32),
            trimTo(event.severity(), 16),
            trimTo(event.resourceType(), 64),
            trimTo(event.resourceId(), 128),
            trimTo(event.resourceName(), 255),
            trimTo(event.outcome(), 16),
            trimTo(event.httpMethod(), 8),
            trimTo(event.requestPath(), 512),
            trimTo(event.clientIp(), 64),
            trimTo(event.userAgent(), 512),
            trimTo(event.nodeId(), 128),
            json(before),
            json(after),
            json(changedFields),
            trimTo(event.errorCode(), 64),
            trimTo(event.errorMessage(), 1000),
            json(event.metadata() == null ? java.util.Map.of() : event.metadata())
        );
        return id == null ? -1 : id;
    }

    public PageResponse<AuditEventDto> search(AuditQuery query)
    {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(query, args);
        long total = jdbcTemplate.queryForObject(
            "select count(*) from public.audit_event " + where,
            Long.class,
            args.toArray()
        );
        String sql = """
            select id, event_id, occurred_at, request_id, actor_type, actor_username, actor_roles,
                   session_id, action, category, severity, resource_type, resource_id, resource_name,
                   outcome, http_method, request_path, cast(client_ip as text) as client_ip, user_agent,
                   node_id, before_data, after_data, changed_fields, error_code, error_message, metadata
              from public.audit_event
            """ + where + " order by occurred_at desc, id desc limit ? offset ?";
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.pageSize());
        pageArgs.add(query.page() * query.pageSize());
        List<AuditEventDto> content = jdbcTemplate.query(sql, this::map, pageArgs.toArray());
        int totalPages = query.pageSize() == 0 ? 0 : (int) Math.ceil((double) total / query.pageSize());
        return new PageResponse<>(content, query.page(), query.pageSize(), total, totalPages, content.isEmpty());
    }

    public AuditEventDto findById(long id)
    {
        List<AuditEventDto> rows = jdbcTemplate.query("""
            select id, event_id, occurred_at, request_id, actor_type, actor_username, actor_roles,
                   session_id, action, category, severity, resource_type, resource_id, resource_name,
                   outcome, http_method, request_path, cast(client_ip as text) as client_ip, user_agent,
                   node_id, before_data, after_data, changed_fields, error_code, error_message, metadata
              from public.audit_event
             where id = ?
            """, this::map, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<AuditEventDto> searchForExport(AuditQuery query, int limit)
    {
        List<Object> args = new ArrayList<>();
        String where = buildWhere(query, args);
        args.add(limit);
        return jdbcTemplate.query("""
            select id, event_id, occurred_at, request_id, actor_type, actor_username, actor_roles,
                   session_id, action, category, severity, resource_type, resource_id, resource_name,
                   outcome, http_method, request_path, cast(client_ip as text) as client_ip, user_agent,
                   node_id, before_data, after_data, changed_fields, error_code, error_message, metadata
              from public.audit_event
            """ + where + " order by occurred_at desc, id desc limit ?", this::map, args.toArray());
    }

    public List<LoginFailureBucket> failedLoginBuckets(OffsetDateTime since, int threshold)
    {
        return jdbcTemplate.query("""
            select coalesce(actor_username, 'unknown') as actor_username,
                   coalesce(cast(client_ip as text), 'unknown') as client_ip,
                   count(*) as event_count
              from public.audit_event
             where action = 'AUTH_LOGIN_FAILED'
               and occurred_at >= ?
             group by actor_username, client_ip
            having count(*) >= ?
            """,
            (rs, rowNum) -> new LoginFailureBucket(
                rs.getString("actor_username"),
                rs.getString("client_ip"),
                rs.getLong("event_count")
            ),
            since,
            threshold
        );
    }

    public List<CriticalEventSummary> criticalEvents(OffsetDateTime since)
    {
        return jdbcTemplate.query("""
            select action, severity, count(*) as event_count
              from public.audit_event
             where occurred_at >= ?
               and severity = 'CRITICAL'
             group by action, severity
             order by count(*) desc, action
            """,
            (rs, rowNum) -> new CriticalEventSummary(
                rs.getString("action"),
                rs.getString("severity"),
                rs.getLong("event_count")
            ),
            since
        );
    }

    public long purgeBefore(OffsetDateTime cutoff, int batchSize)
    {
        long deleted = 0;
        int affected;
        do {
            affected = jdbcTemplate.update("""
                delete from public.audit_event
                 where id in (
                    select id from public.audit_event
                     where occurred_at < ?
                     order by occurred_at
                     limit ?
                 )
                """, cutoff, batchSize);
            deleted += affected;
        }
        while (affected == batchSize);
        return deleted;
    }

    private String buildWhere(AuditQuery query, List<Object> args)
    {
        List<String> clauses = new ArrayList<>();
        if (query.from() != null) {
            clauses.add("occurred_at >= ?");
            args.add(query.from());
        }
        if (query.to() != null) {
            clauses.add("occurred_at <= ?");
            args.add(query.to());
        }
        addEquals(clauses, args, "actor_username", query.actor());
        addEquals(clauses, args, "action", query.action());
        addEquals(clauses, args, "outcome", query.outcome());
        addEquals(clauses, args, "severity", query.severity());
        addEquals(clauses, args, "resource_type", query.resourceType());
        addEquals(clauses, args, "resource_id", query.resourceId());
        addEquals(clauses, args, "request_id", query.requestId());
        if (query.q() != null && !query.q().isBlank()) {
            clauses.add("""
                (actor_username ilike ? or action ilike ? or resource_name ilike ?
                 or resource_id ilike ? or request_id ilike ?)
                """);
            String pattern = "%" + query.q().trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    private void addEquals(List<String> clauses, List<Object> args, String column, String value)
    {
        if (value != null && !value.isBlank()) {
            clauses.add(column + " = ?");
            args.add(value.trim());
        }
    }

    private AuditEventDto map(ResultSet rs, int rowNum) throws SQLException
    {
        return new AuditEventDto(
            rs.getLong("id"),
            rs.getObject("event_id", java.util.UUID.class),
            rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
            rs.getString("request_id"),
            rs.getString("actor_type"),
            rs.getString("actor_username"),
            json(rs.getString("actor_roles")),
            rs.getString("session_id"),
            rs.getString("action"),
            rs.getString("category"),
            rs.getString("severity"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("resource_name"),
            rs.getString("outcome"),
            rs.getString("http_method"),
            rs.getString("request_path"),
            rs.getString("client_ip"),
            rs.getString("user_agent"),
            rs.getString("node_id"),
            json(rs.getString("before_data")),
            json(rs.getString("after_data")),
            json(rs.getString("changed_fields")),
            rs.getString("error_code"),
            rs.getString("error_message"),
            json(rs.getString("metadata"))
        );
    }

    private JsonNode json(String value)
    {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        }
        catch (Exception ex) {
            return null;
        }
    }

    private String json(Object value)
    {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception ex) {
            return "null";
        }
    }

    private String trimTo(String value, int maxLength)
    {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record AuditQuery(
        OffsetDateTime from,
        OffsetDateTime to,
        String actor,
        String action,
        String outcome,
        String severity,
        String resourceType,
        String resourceId,
        String requestId,
        String q,
        int page,
        int pageSize
    )
    {
    }

    public record LoginFailureBucket(String actor, String clientIp, long count)
    {
    }

    public record CriticalEventSummary(String action, String severity, long count)
    {
    }
}
