package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "etl_table_change_log")
@Data
public class EtlTableChangeLog
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid", nullable = false)
    private Long tid;

    @Column(name = "source_db", length = 32)
    private String sourceDb;

    @Column(name = "source_table", length = 64)
    private String sourceTable;

    @Column(name = "target_db", length = 50)
    private String targetDb;

    @Column(name = "target_table", length = 200)
    private String targetTable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", columnDefinition = "jsonb", nullable = false)
    private JsonNode changedFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb", nullable = false)
    private JsonNode oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb", nullable = false)
    private JsonNode newValues;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @Column(name = "changed_by", length = 100)
    private String changedBy;
}
