package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "vw_imp_check_soutab")
@Setter
@Getter
public class VwImpCheckSoutab {

    @Id
    @Column(name = "sou_db_conn")
    private String souDbConn;

    @Column(name = "owner")
    private String owner;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "column_name")
    private String columnName;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "data_type_last")
    private String dataTypeLast;

    @Column(name = "hive_type")
    private String hiveType;

    @Column(name = "hive_type_last")
    private String hiveTypeLast;

    @Column(name = "data_length")
    private Integer dataLength;

    @Column(name = "data_length_last")
    private Integer dataLengthLast;

    @Column(name = "data_precision")
    private Integer dataPrecision;

    @Column(name = "data_precision_last")
    private Integer dataPrecisionLast;

    @Column(name = "data_scale")
    private Integer dataScale;

    @Column(name = "data_scale_last")
    private Integer dataScaleLast;

    @Column(name = "dw_clt_date")
    private Date dwCltDate;

    @Column(name = "dw_clt_date_last")
    private Date dwCltDateLast;
}
