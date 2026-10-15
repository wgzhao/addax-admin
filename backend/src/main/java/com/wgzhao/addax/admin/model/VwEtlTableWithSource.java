package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.common.TargetOptions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 映射 vw_etl_table_with_source 视图
 */
@Entity
@Table(name = "vw_etl_table_with_source")
@Data
public class VwEtlTableWithSource
{
    @Id
    private Long id;
    private String sourceDb;
    private String sourceTable;
    private String targetDb;
    private String targetTable;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_options")
    private JsonNode targetOptions;
    private String filter;
    private String status;
    private String kind;
    private Integer retryCnt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
    private Integer sid;
    private Integer duration;
    private String code;
    private String name;
    private String url;
    private String username;
    private String pass;
    private String dbType;
    /**
     * 采集源的调度时间点（HH:mm:ss / HH:mm）
     */
    private String sourceStartAt;

    /**
     * 采集源的采集日历（自然日/工作日/交易日），为空表示自然日
     */
    private String calendar;

    /**
     * 表级调度时间点（HH:mm:ss / HH:mm），可为空，表示继承 sourceStartAt
     */
    private String startAt;
    private Boolean enabled;
    private String tblComment;
    // Only the partition column name is searchable, not the whole bag: matching target_options::text made
    // a search for "hdfs" (or a storage format) hit every Hive table.
    @Formula("UPPER(concat_ws('|', id, source_db || '.' || source_table , target_db || '.' || target_table , target_options->'hdfs'->>'partName' , code, name))")
    private String filterColumn;
    private Integer maxConcurrency;
    private String splitPk;
    private Boolean autoPk;
    private String writeMode;
    private Long targetId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reader_plugin_config")
    private JsonNode readerPluginConfig;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "writer_plugin_config")
    private JsonNode writerPluginConfig;
    private String targetType;
    private String targetCode;
    private String targetName;
    private Boolean targetEnabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    // ---- derived target attributes (stored in target_options, read-only here) -------------------

    @JsonIgnore
    public String getPartKind()
    {
        return TargetOptions.hdfsPartKind(targetOptions);
    }

    @JsonIgnore
    public String getPartName()
    {
        return TargetOptions.hdfsPartName(targetOptions);
    }

    @JsonIgnore
    public String getPartFormat()
    {
        return TargetOptions.hdfsPartFormat(targetOptions);
    }

    @JsonIgnore
    public String getStorageFormat()
    {
        return TargetOptions.hdfsStorageFormat(targetOptions);
    }

    @JsonIgnore
    public String getCompressFormat()
    {
        return TargetOptions.hdfsCompressFormat(targetOptions);
    }

    @JsonIgnore
    public String getRdbmsBusinessDateColumn()
    {
        return TargetOptions.rdbmsBusinessDateColumn(targetOptions);
    }

    @JsonIgnore
    public boolean isRdbmsCreatePrimaryKey()
    {
        return TargetOptions.rdbmsCreatePrimaryKey(targetOptions);
    }
}
