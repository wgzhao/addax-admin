package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记录表结构变化，用于前端展示每日表结构差异
 */
@Entity
@Table(name = "schema_change_log")
@Data
public class SchemaChangeLog
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid")
    private Long tid;

    @Column(name = "source_db", length = 64)
    private String sourceDb;

    @Column(name = "source_table", length = 128)
    private String sourceTable;

    @Column(name = "column_name", length = 255)
    private String columnName;

    /**
     * ADD / DELETE / TYPE_CHANGE
     */
    @Column(name = "change_type", length = 32)
    private String changeType;

    @Column(name = "old_source_type", length = 128)
    private String oldSourceType;

    @Column(name = "new_source_type", length = 128)
    private String newSourceType;

    @Column(name = "old_data_length")
    private Integer oldDataLength;

    @Column(name = "new_data_length")
    private Integer newDataLength;

    @Column(name = "old_data_precision")
    private Integer oldDataPrecision;

    @Column(name = "new_data_precision")
    private Integer newDataPrecision;

    @Column(name = "old_data_scale")
    private Integer oldDataScale;

    @Column(name = "new_data_scale")
    private Integer newDataScale;

    @Column(name = "old_col_comment", length = 2000)
    private String oldColComment;

    @Column(name = "new_col_comment", length = 2000)
    private String newColComment;

    @Column(name = "change_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime changeAt;
}

