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
 * TB_IMP_TBL_HDP 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_TBL_HDP")
@Setter
@Getter
@Data
public class ImpTblHdp {

    
    // TID

    @Column(name = "TID") 
    private String tid;

    
    // HIVE_OWNER

    @Column(name = "HIVE_OWNER") 
    private String hiveOwner;

    
    // HIVE_TABLENAME

    @Column(name = "HIVE_TABLENAME") 
    private String hiveTablename;

    
    // COL_NAME
    @Id
    @Column(name = "COL_NAME") 
    private String colName;

    
    // COL_TYPE_FULL

    @Column(name = "COL_TYPE_FULL") 
    private String colTypeFull;

    
    // COL_TYPE

    @Column(name = "COL_TYPE") 
    private String colType;

    
    // COL_PRECISION

    @Column(name = "COL_PRECISION") 
    private Long colPrecision;

    
    // COL_SCALE

    @Column(name = "COL_SCALE") 
    private Long colScale;

    
    // COL_IDX

    @Column(name = "COL_IDX") 
    private Long colIdx;

    
    // TBL_COMMENT

    @Column(name = "TBL_COMMENT") 
    private String tblComment;

    
    // COL_COMMENT

    @Column(name = "COL_COMMENT") 
    private String colComment;

    
    // UPDT_DATE

    @Column(name = "UPDT_DATE") 
    private Date updtDate;

    
    // CD_ID

    @Column(name = "CD_ID") 
    private Long cdId;

}