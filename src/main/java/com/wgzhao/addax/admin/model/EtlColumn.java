package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "etl_column")
@Data
@IdClass(EtlColumnPk.class)
public class EtlColumn {

    @Id
    @Column(name = "tid", nullable = false)
    private Long tid;

    @Id
    @Column(name = "column_name", length = 255)
    private String columnName;

    @Column(name = "column_id")
    private int columnId;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "data_length")
    private int dataLength;

    @Column(name = "data_precision")
    private Integer dataPrecision;

    @Column(name = "data_scale")
    private Integer dataScale;

    @Column(name = "col_comment", length = 4000)
    private String colComment;

    @Column(name = "target_type", length = 50, nullable = false)
    private String targetType;

    @Column(name = "target_type_full", length = 100)
    private String targetTypeFull;

    @Column(name = "update_at")
    private Timestamp updateAt;
}
