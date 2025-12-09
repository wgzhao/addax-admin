package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * TB_IMP_ETL 实体类
 *
 * @author wgzhao
 */
@Entity
@Table(name = "etl_table")
@Setter
@Getter
@Data
public class EtlTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_db", length = 32, nullable = false)
    private String sourceDb;

    @Column(name = "source_table", length = 64, nullable = false)
    private String sourceTable;

    @Column(name = "target_db", length = 50, nullable = false)
    private String targetDb;

    @Column(name = "target_table", length = 200, nullable = false)
    private String targetTable;

    @Column(name = "part_kind", length = 1)
    private String partKind;

    @Column(name = "part_name", length = 20)
    private String partName;

    @Column(name = "part_format", length = 10)
    private String partFormat;

    @Column(name = "storage_format", length=10)
    private String storageFormat;

    @Column(name = "compress_format", length=10)
    private String compressFormat;

    @Column(name = "filter", length = 2000, nullable = false)
    private String filter;

    @Column(name = "status", length = 1)
    private String status;

    @Column(name = "kind", length = 1)
    private String kind;

    @Column(name = "retry_cnt")
    private Integer retryCnt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "start_time")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "max_runtime")
    private Integer maxRuntime;

    @Column(name = "sid")
    private Integer sid;

    @Column(name = "duration", nullable = false)
    private Long duration;

    @Column(name = "tbl_comment", length = 500)
    private String tblComment;

    @Column(name = "split_pk", length = 64)
    private String splitPk = "";

    @Column(name = "auto_pk", nullable = false)
    private Boolean autoPk = true;

    @Formula("LOWER(concat_ws(',', source_db || '.' || source_table , target_db || '.' || target_table , part_kind , part_name , filter))")
    private String filterColumn;
}