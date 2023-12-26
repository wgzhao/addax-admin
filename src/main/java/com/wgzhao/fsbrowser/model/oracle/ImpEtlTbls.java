package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * HIVE的表结构信息 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_ETL_TBLS")
@Setter
@Getter
@Data
public class ImpEtlTbls {

    
    // DB_ID

    @Column(name = "DB_ID") 
    private Long dbId;

    
    // 数据库名、用户
    @Id
    @Column(name = "DB_NAME") 
    private String dbName;

    
    // 数据库所在HDFS地址

    @Column(name = "DB_LOCATION") 
    private String dbLocation;

    
    // TBL_ID

    @Column(name = "TBL_ID") 
    private Long tblId;

    
    // 表名

    @Column(name = "TBL_NAME") 
    private String tblName;

    
    // 表类型（管理表、外部表）

    @Column(name = "TBL_TYPE") 
    private String tblType;

    
    // 表所在HDFS地址

    @Column(name = "TBL_LOCATION") 
    private String tblLocation;

    
    // CD_ID

    @Column(name = "CD_ID") 
    private Long cdId;

    
    // 字段名

    @Column(name = "COL_NAME") 
    private String colName;

    
    // 字段类型

    @Column(name = "COL_TYPE") 
    private String colType;

    
    // 字段备注

    @Column(name = "COL_COMMENT") 
    private String colComment;

    
    // 字段顺序

    @Column(name = "COL_IDX") 
    private Long colIdx;

    
    // 表注释

    @Column(name = "TBL_COMMENT") 
    private String tblComment;

}