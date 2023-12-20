package com.wgzhao.fsbrowser.model;

import jakarta.persistence.*;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * TB_IMP_ETL_SOUTAB 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_ETL_SOUTAB")
@Setter
@Getter
@Data
public class ImpEtlSoutab {

    
    // SOU_DB_CONN
    @Id
    @Column(name = "SOU_DB_CONN") 
    private String souDbConn;

    
    // OWNER

    @Column(name = "OWNER") 
    private String owner;

    
    // TABLE_NAME

    @Column(name = "TABLE_NAME") 
    private String tableName;

    
    // COLUMN_NAME

    @Column(name = "COLUMN_NAME") 
    private String columnName;

    
    // DATA_TYPE

    @Column(name = "DATA_TYPE") 
    private String dataType;

    
    // DATA_LENGTH

    @Column(name = "DATA_LENGTH") 
    private Long dataLength;

    
    // DATA_PRECISION

    @Column(name = "DATA_PRECISION") 
    private Long dataPrecision;

    
    // DATA_SCALE

    @Column(name = "DATA_SCALE") 
    private Long dataScale;

    
    // COLUMN_ID

    @Column(name = "COLUMN_ID") 
    private Long columnId;

    
    // TABLE_TYPE

    @Column(name = "TABLE_TYPE") 
    private String tableType;

    
    // TAB_COMMENT

    @Column(name = "TAB_COMMENT") 
    private String tabComment;

    
    // COL_COMMENT

    @Column(name = "COL_COMMENT") 
    private String colComment;

    
    // DW_CLT_DATE

    @Column(name = "DW_CLT_DATE") 
    private Date dwCltDate;

}