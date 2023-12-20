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
 * TB_IMP_PLAN 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_PLAN")
@Setter
@Getter
@Data
public class ImpPlan {

    
    // 定时计划类型，参考参数1064

    @Column(name = "PN_TYPE") 
    private String pnType;

    
    // 定点时间

    @Column(name = "PN_FIXED") 
    private String pnFixed;

    
    // 轮询间隔

    @Column(name = "PN_INTERVAL") 
    private Long pnInterval;

    
    // 轮询时间区间

    @Column(name = "PN_RANGE") 
    private String pnRange;

    
    // PN_ID
    @Id
    @Column(name = "PN_ID") 
    private String pnId;

    
    // FLAG

    @Column(name = "FLAG") 
    private String flag;

    
    // START_TIME

    @Column(name = "START_TIME") 
    private Date startTime;

    
    // END_TIME

    @Column(name = "END_TIME") 
    private Date endTime;

    
    // 是否跳过切日时间段

    @Column(name = "BEXIT") 
    private String bexit;

    
    // RUNTIME

    @Column(name = "RUNTIME") 
    private Long runtime;

}