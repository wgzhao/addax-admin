package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.common.DbType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "etl_source")
@Setter
@Getter
@Data
public class EtlSource
{
    /** Sentinel returned to the client instead of the real password. */
    public static final String PASS_SENTINEL = "**UNCHANGED**";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    /**
     * Legacy display label kept for one release so old clients and rolling nodes still agree on the
     * wire contract. It no longer drives target-database naming, scheduling identity or statistics;
     * the primary key is the source identity.
     *
     * @deprecated use {@link #id} or {@link #name}; scheduled for removal in Phase 2.
     */
    @Deprecated
    @Column(name = "code", length = 10, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "url", length = 1000, nullable = false)
    private String url;

    @Column(name = "username", length = 64)
    private String username;

    @JsonIgnore   // suppress Lombok's getPass() from serialization
    @Column(name = "pass", length = 64)
    private String pass;

    // Always serializes as the sentinel so the real password never crosses the wire.
    @JsonProperty("pass")
    public String getPassDisplay()
    {
        return PASS_SENTINEL;
    }

    // Accepts whatever the client sends (sentinel or new password); the service
    // layer decides whether to persist it.
    @JsonProperty("pass")
    public void setPassDisplay(String value)
    {
        this.pass = value;
    }

    @Column(name = "start_at")
    private LocalTime startAt;

    /**
     * Collection calendar name ({@link com.wgzhao.addax.admin.common.CalendarType}), blank means natural day.
     * Row-level, not table-level: a business system either works on a day or it does not.
     */
    @Column(name = "calendar", length = 32)
    private String calendar;

    @Column(name = "prerequisite", length = 4000)
    private String prerequisite;

    @Column(name = "pre_script", length = 4000)
    private String preScript;

    @Column(name = "remark", length = 2000)
    private String remark;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "max_concurrency")
    private Integer maxConcurrency = 5;

    @Column(name = "db_type", length = 50)
    private String dbType = DbType.RDBMS.getValue();

    /**
     * Structured connection settings for non-JDBC sources (MongoDB, Elasticsearch). JDBC sources keep
     * everything in {@link #url}; this column is null for them. Secrets stay in
     * {@link #username}/{@link #pass}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "connect_config", columnDefinition = "jsonb")
    private JsonNode connectConfig;

    // ===== computed fields for UI =====
    @org.hibernate.annotations.Formula("(select count(1) from etl_table t where t.sid = id)")
    private Long tableCount;

    @org.hibernate.annotations.Formula("(select count(1) from etl_table t where t.sid = id and t.status <> 'X' and t.retired_at is null)")
    private Long validTableCount;
}

