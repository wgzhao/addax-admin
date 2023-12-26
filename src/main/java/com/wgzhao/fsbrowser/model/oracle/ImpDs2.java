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
 * TB_IMP_DS2 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_DS2")
@Setter
@Getter
@Data
public class ImpDs2 {

    
    // TASK_GROUP

    @Column(name = "TASK_GROUP") 
    private String taskGroup;

    
    // DEST_SYSID

    @Column(name = "DEST_SYSID") 
    private String destSysid;

    
    // DEST_OWNER

    @Column(name = "DEST_OWNER") 
    private String destOwner;

    
    // FLAG

    @Column(name = "FLAG") 
    private String flag;

    
    // PARAM_SOU

    @Column(name = "PARAM_SOU") 
    private String paramSou;

    
    // RETRY_CNT

    @Column(name = "RETRY_CNT") 
    private Long retryCnt;

    
    // START_TIME

    @Column(name = "START_TIME") 
    private Date startTime;

    
    // END_TIME

    @Column(name = "END_TIME") 
    private Date endTime;

    
    // DS_ID
    @Id
    @Column(name = "DS_ID") 
    private String dsId;

    
    // PRE_SQL

    @Column(name = "PRE_SQL") 
    private String preSql;

    
    // POST_SQL

    @Column(name = "POST_SQL") 
    private String postSql;

    
    // COL_MAP

    @Column(name = "COL_MAP") 
    private String colMap;

    
    // 执行并发数

    @Column(name = "PARAL_NUM") 
    private Long paralNum;

    
    // BUPDATE

    @Column(name = "BUPDATE") 
    private String bupdate;

    
    // RUNTIME

    @Column(name = "RUNTIME") 
    private Long runtime;

    
    // PRE_SH

    @Column(name = "PRE_SH") 
    private String preSh;

    
    // POST_SH

    @Column(name = "POST_SH") 
    private String postSh;

    
    // 允许最长耗时

    @Column(name = "MAX_RUNTIME") 
    private Long maxRuntime;

    
    // 运行频率，周期(D天,W周,M月,Q季度,Y年度)

    @Column(name = "RUN_FREQ") 
    private String runFreq;

    
    // PN_FIXED

    @Column(name = "PN_FIXED") 
    private String pnFixed;

    
    // PN_INTERVAL

    @Column(name = "PN_INTERVAL") 
    private Long pnInterval;

    
    // PN_RANGE

    @Column(name = "PN_RANGE") 
    private String pnRange;

    
    // PN_TYPE

    @Column(name = "PN_TYPE") 
    private String pnType;

    
    // REMARK

    @Column(name = "REMARK") 
    private String remark;

}