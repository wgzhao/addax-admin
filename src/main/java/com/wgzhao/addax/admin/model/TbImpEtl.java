package com.wgzhao.addax.admin.model;

import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

/**
 * TB_IMP_ETL 实体类
 *
 * @author 
 */
@Entity
@Table(name="tb_imp_etl")
@Setter
@Getter
@Data
public class TbImpEtl {

    
    // 源系统编号

    @Column(name = "sou_sysid") 
    private String souSysid;

    
    // 源用户

    @Column(name = "sou_owner") 
    private String souOwner;

    
    // 源表

    @Column(name = "sou_tablename") 
    private String souTablename;

    
    // 源筛选条件

    @Column(name = "sou_filter") 
    private String souFilter;

    
    // 目标表名

    @Column(name = "dest_tablename") 
    private String destTablename;

    
    // 入库规则

    @Column(name = "dest_part_kind") 
    private String destPartKind;

    
    // 运行状态

    @Column(name = "flag") 
    private String flag;

    
    // 参数组

    @Column(name = "param_sou") 
    private String paramSou;

    
    // 是否更新表的元数据信息

    @Column(name = "bupdate") 
    private String bupdate;

    
    // 是否在hadoop上建表

    @Column(name = "bcreate") 
    private String bcreate;

    
    // 重试次数

    @Column(name = "retry_cnt") 
    private Long retryCnt;

    
    // 采集开始时间

    @Column(name = "start_time") 
    private Date startTime;

    
    // 采集结束时间

    @Column(name = "end_time") 
    private Date endTime;

    
    // RUNTIME

    @Column(name = "runtime") 
    private Long runtime;

    
    // TID
    @Id
    @Column(name = "tid")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String tid;

    
    // SOU_SPLIT

    @Column(name = "sou_split") 
    private String souSplit;

    
    // REMARK

    @Column(name = "remark") 
    private String remark;

    
    // 该表目前的采集模式:A盘后采集,R实时采集,默认A

    @Column(name = "etl_kind") 
    private String etlKind;

    
    // 是否推送至预发布环境

    @Column(name = "bpreview") 
    private String bpreview;

    
    // 实时频率（分钟），0为不开启实时

    @Column(name = "realtime_interval") 
    private Long realtimeInterval;

    
    // 实时任务的任务组，盘后采集的任务组为数据源编号

    @Column(name = "realtime_taskgroup") 
    private String realtimeTaskgroup;

    
    // 实时采集时的源表用户,为空取sou_owner

    @Column(name = "realtime_sou_owner") 
    private String realtimeSouOwner;

    
    // 实时定点采集，非间隔

    @Column(name = "realtime_fixed") 
    private String realtimeFixed;

    
    // 实时采集时的源表筛选条件

    @Column(name = "realtime_sou_filter") 
    private String realtimeSouFilter;

    
    // 实时频率的时间范围

    @Column(name = "realtime_interval_range") 
    private String realtimeIntervalRange;

    
    // 盘后任务重采定时

    @Column(name = "after_retry_fixed") 
    private String afterRetryFixed;

    
    // 盘后任务重采计划

    @Column(name = "after_retry_pntype") 
    private String afterRetryPntype;

    
    // 是否推送至星环hadoop

    @Column(name = "btdh") 
    private String btdh;

    
    // 运行时间增加，仅用于任务提前执行

    @Column(name = "runtime_add") 
    private Long runtimeAdd;

    // virtual column
    @Formula("upper(concat_ws(',','ods' || lower(sou_sysid) || '.' || dest_tablename, sou_owner,sou_tablename,sou_filter))")
    private String filterColumn;
}