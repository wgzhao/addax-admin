package com.wgzhao.fsbrowser.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * HADOOP_SP的配置主表 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_SP")
@Setter
@Getter
@Data
public class ImpSp {

    
    // SP用户

    @Column(name = "SP_OWNER") 
    private String spOwner;

    
    // SP名称

    @Column(name = "SP_NAME") 
    private String spName;

    
    // SP的主键，自动生成，唯一性
    @Id
    @Column(name = "SP_ID") 
    private String spId;

    
    // 运行状态

    @Column(name = "FLAG") 
    private String flag;

    
    // 运行频率，周期(D天,W周,M月,Q季度,Y年度)

    @Column(name = "RUN_FREQ") 
    private String runFreq;

    
    // 运行开始时间

    @Column(name = "START_TIME")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    
    // 运行结束时间

    @Column(name = "END_TIME")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    
    // 错误重试次数

    @Column(name = "RETRY_CNT") 
    private Long retryCnt;

    
    // 任务运行时间，用来并发排序

    @Column(name = "RUNTIME") 
    private Long runtime;

    
    // 所属任务组，用于调起后续工作流

    @Column(name = "TASK_GROUP") 
    private String taskGroup;

    
    // 参数文件组（L昨日，C当日，N下日）

    @Column(name = "PARAM_SOU") 
    private String paramSou;

    
    // 备注信息

    @Column(name = "REMARK") 
    private String remark;

    
    // 前置采集任务组(专用了等待实时的采集任务组)

    @Column(name = "REALTIME_TASKGROUP") 
    private String realtimeTaskgroup;

    
    // 计划类型

    @Column(name = "PN_TYPE") 
    private String pnType;

    
    // 计划定点时间

    @Column(name = "PN_FIXED") 
    private String pnFixed;

    
    // 计划间隔时间

    @Column(name = "PN_INTERVAL") 
    private Long pnInterval;

    
    // 计划间隔时间范围

    @Column(name = "PN_RANGE") 
    private String pnRange;

}