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
 * TB_IMP_DS2_TBLS 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_DS2_TBLS")
@Setter
@Getter
@Data
public class ImpDs2Tbls {

    
    // DS_ID

    @Column(name = "DS_ID") 
    private String dsId;

    
    // 是否从hdp推送

    @Column(name = "SOU_ISHDP") 
    private String souIshdp;

    
    // SOU_TABLE

    @Column(name = "SOU_TABLE") 
    private String souTable;

    
    // 源筛选条件

    @Column(name = "SOU_FILTER") 
    private String souFilter;

    
    // DEST_OWNER

    @Column(name = "DEST_OWNER") 
    private String destOwner;

    
    // DEST_TABLENAME

    @Column(name = "DEST_TABLENAME") 
    private String destTablename;

    
    // FLAG

    @Column(name = "FLAG") 
    private String flag;

    
    // START_TIME

    @Column(name = "START_TIME") 
    private Date startTime;

    
    // END_TIME

    @Column(name = "END_TIME") 
    private Date endTime;

    
    // TBL_ID
    @Id
    @Column(name = "TBL_ID") 
    private String tblId;

    
    // PRE_SQL

    @Column(name = "PRE_SQL") 
    private String preSql;

    
    // COL_MAP

    @Column(name = "COL_MAP") 
    private String colMap;

    
    // POST_SQL

    @Column(name = "POST_SQL") 
    private String postSql;

    
    // 允许最长耗时

    @Column(name = "MAX_RUNTIME") 
    private Long maxRuntime;

}