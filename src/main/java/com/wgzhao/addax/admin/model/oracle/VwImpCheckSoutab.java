package com.wgzhao.addax.admin.model.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "VW_IMP_CHECK_SOUTAB")
@Setter
@Getter
public class VwImpCheckSoutab {

    @Id
    @Column(name = "SOU_DB_CONN")
    private String souDbConn;

    @Column(name = "OWNER")
    private String owner;

    @Column(name = "TABLE_NAME")
    private String tableName;

    @Column(name = "COLUMN_NAME")
    private String columnName;

    @Column(name = "DATA_TYPE")
    private String dataType;

    @Column(name = "DATA_TYPE_LAST")
    private String dataTypeLast;

    @Column(name = "HIVE_TYPE")
    private String hiveType;

    @Column(name = "HIVE_TYPE_LAST")
    private String hiveTypeLast;

    @Column(name = "DATA_LENGTH")
    private Integer dataLength;

    @Column(name = "DATA_LENGTH_LAST")
    private Integer dataLengthLast;

    @Column(name = "DATA_PRECISION")
    private Integer dataPrecision;

    @Column(name = "DATA_PRECISION_LAST")
    private Integer dataPrecisionLast;

    @Column(name = "DATA_SCALE")
    private Integer dataScale;

    @Column(name = "DATA_SCALE_LAST")
    private Integer dataScaleLast;

    @Column(name = "DW_CLT_DATE")
    private Date dwCltDate;

    @Column(name = "DW_CLT_DATE_LAST")
    private Date dwCltDateLast;
}
