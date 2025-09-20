package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.util.Date;

/**
 * TB_IMP_ETL 实体类
 *
 * @author
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

    @Column(name = "filter", length = 2000, nullable = false)
    private String filter;

    @Column(name = "status", length = 1)
    private String status;

    @Column(name = "kind", length = 1)
    private String kind;

    @Column(name = "update_flag", length = 1)
    private String updateFlag;

    @Column(name = "create_flag", length = 1)
    private String createFlag;

    @Column(name = "retry_cnt")
    private Integer retryCnt;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "max_runtime")
    private Integer maxRuntime;

    @ManyToOne
    @JoinColumn(name = "sid", referencedColumnName = "id")
    private EtlSource etlSource;

    @Column(name = "duration", nullable = false)
    private Long duration;

    @Formula("LOWER(source_db || '.' || source_table || target_db || '.' || target_table || part_kind || part_name || filter)")
    private String filterColumn;
}