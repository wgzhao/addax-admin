package com.wgzhao.addax.admin.model.oracle;

import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * TB_IMP_ETL 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_ETL")
@Setter
@Getter
@Data
public class TbImpEtl {

    
    // 源系统编号

    @Column(name = "SOU_SYSID") 
    private String souSysid;

    
    // 源用户

    @Column(name = "SOU_OWNER") 
    private String souOwner;

    
    // 源表

    @Column(name = "SOU_TABLENAME") 
    private String souTablename;

    
    // 源筛选条件

    @Column(name = "SOU_FILTER") 
    private String souFilter;

    
    // 目标表名

    @Column(name = "DEST_TABLENAME") 
    private String destTablename;

    
    // 入库规则

    @Column(name = "DEST_PART_KIND") 
    private String destPartKind;

    
    // 运行状态

    @Column(name = "FLAG") 
    private String flag;

    
    // 参数组

    @Column(name = "PARAM_SOU") 
    private String paramSou;

    
    // 是否更新表的元数据信息

    @Column(name = "BUPDATE") 
    private String bupdate;

    
    // 是否在hadoop上建表

    @Column(name = "BCREATE") 
    private String bcreate;

    
    // 重试次数

    @Column(name = "RETRY_CNT") 
    private Long retryCnt;

    
    // 采集开始时间

    @Column(name = "START_TIME") 
    private Date startTime;

    
    // 采集结束时间

    @Column(name = "END_TIME") 
    private Date endTime;

    
    // RUNTIME

    @Column(name = "RUNTIME") 
    private Long runtime;

    
    // TID
    @Id
    @Column(name = "TID") 
    private String tid;

    
    // SOU_SPLIT

    @Column(name = "SOU_SPLIT") 
    private String souSplit;

    
    // REMARK

    @Column(name = "REMARK") 
    private String remark;

    
    // 该表目前的采集模式:A盘后采集,R实时采集,默认A

    @Column(name = "ETL_KIND") 
    private String etlKind;

    
    // 是否推送至预发布环境

    @Column(name = "BPREVIEW") 
    private String bpreview;

    
    // 实时频率（分钟），0为不开启实时

    @Column(name = "REALTIME_INTERVAL") 
    private Long realtimeInterval;

    
    // 实时任务的任务组，盘后采集的任务组为数据源编号

    @Column(name = "REALTIME_TASKGROUP") 
    private String realtimeTaskgroup;

    
    // 实时采集时的源表用户,为空取sou_owner

    @Column(name = "REALTIME_SOU_OWNER") 
    private String realtimeSouOwner;

    
    // 实时定点采集，非间隔

    @Column(name = "REALTIME_FIXED") 
    private String realtimeFixed;

    
    // 实时采集时的源表筛选条件

    @Column(name = "REALTIME_SOU_FILTER") 
    private String realtimeSouFilter;

    
    // 实时频率的时间范围

    @Column(name = "REALTIME_INTERVAL_RANGE") 
    private String realtimeIntervalRange;

    
    // 盘后任务重采定时

    @Column(name = "AFTER_RETRY_FIXED") 
    private String afterRetryFixed;

    
    // 盘后任务重采计划

    @Column(name = "AFTER_RETRY_PNTYPE") 
    private String afterRetryPntype;

    
    // 是否推送至星环hadoop

    @Column(name = "BTDH") 
    private String btdh;

    
    // 运行时间增加，仅用于任务提前执行

    @Column(name = "RUNTIME_ADD") 
    private Long runtimeAdd;

}