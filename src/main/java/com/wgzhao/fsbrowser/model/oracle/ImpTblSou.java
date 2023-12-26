package com.wgzhao.fsbrowser.model.oracle;

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
 * TB_IMP_TBL_SOU 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_TBL_SOU")
@Setter
@Getter
@Data
public class ImpTblSou {

    
    // TID

    @Column(name = "TID") 
    private String tid;

    
    // SOU_DB_CONN

    @Column(name = "SOU_DB_CONN") 
    private String souDbConn;

    
    // SOU_OWNER

    @Column(name = "SOU_OWNER") 
    private String souOwner;

    
    // SOU_TABLENAME

    @Column(name = "SOU_TABLENAME") 
    private String souTablename;

    
    // COLUMN_NAME_ORIG

    @Column(name = "COLUMN_NAME_ORIG") 
    private String columnNameOrig;

    
    // COLUMN_NAME
    @Id
    @Column(name = "COLUMN_NAME") 
    private String columnName;

    
    // COLUMN_ID

    @Column(name = "COLUMN_ID") 
    private Long columnId;

    
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

    
    // TBL_COMMENT

    @Column(name = "TBL_COMMENT") 
    private String tblComment;

    
    // COL_COMMENT

    @Column(name = "COL_COMMENT") 
    private String colComment;

    
    // DEST_TYPE

    @Column(name = "DEST_TYPE") 
    private String destType;

    
    // DEST_TYPE_FULL

    @Column(name = "DEST_TYPE_FULL") 
    private String destTypeFull;

    
    // UPDT_DATE

    @Column(name = "UPDT_DATE") 
    private Date updtDate;

}