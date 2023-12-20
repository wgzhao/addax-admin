package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_ETL实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpEtlDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 源系统编号
     */
    private String souSysid;

    /**
     * 源用户
     */
    private String souOwner;

    /**
     * 源表
     */
    private String souTablename;

    /**
     * 源筛选条件
     */
    private String souFilter;

    /**
     * 目标表名
     */
    private String destTablename;

    /**
     * 入库规则
     */
    private String destPartKind;

    /**
     * 运行状态
     */
    private String flag;

    /**
     * 参数组
     */
    private String paramSou;

    /**
     * 是否更新表的元数据信息
     */
    private String bupdate;

    /**
     * 是否在hadoop上建表
     */
    private String bcreate;

    /**
     * 重试次数
     */
    private Long retryCnt;

    /**
     * 采集开始时间
     */
    private Date startTime;

    /**
     * 采集结束时间
     */
    private Date endTime;

    /**
     * RUNTIME
     */
    private Long runtime;

    /**
     * TID
     */
    private String tid;

    /**
     * SOU_SPLIT
     */
    private String souSplit;

    /**
     * REMARK
     */
    private String remark;

    /**
     * 该表目前的采集模式:A盘后采集,R实时采集,默认A
     */
    private String etlKind;

    /**
     * 是否推送至预发布环境
     */
    private String bpreview;

    /**
     * 实时频率（分钟），0为不开启实时
     */
    private Long realtimeInterval;

    /**
     * 实时任务的任务组，盘后采集的任务组为数据源编号
     */
    private String realtimeTaskgroup;

    /**
     * 实时采集时的源表用户,为空取sou_owner
     */
    private String realtimeSouOwner;

    /**
     * 实时定点采集，非间隔
     */
    private String realtimeFixed;

    /**
     * 实时采集时的源表筛选条件
     */
    private String realtimeSouFilter;

    /**
     * 实时频率的时间范围
     */
    private String realtimeIntervalRange;

    /**
     * 盘后任务重采定时
     */
    private String afterRetryFixed;

    /**
     * 盘后任务重采计划
     */
    private String afterRetryPntype;

    /**
     * 是否推送至星环hadoop
     */
    private String btdh;

    /**
     * 运行时间增加，仅用于任务提前执行
     */
    private Long runtimeAdd;

    public String getSouSysid() {
        return this.souSysid;
    }

    public void setSouSysid(String souSysid) {
        this.souSysid = souSysid;
    }

    public String getSouOwner() {
        return this.souOwner;
    }

    public void setSouOwner(String souOwner) {
        this.souOwner = souOwner;
    }

    public String getSouTablename() {
        return this.souTablename;
    }

    public void setSouTablename(String souTablename) {
        this.souTablename = souTablename;
    }

    public String getSouFilter() {
        return this.souFilter;
    }

    public void setSouFilter(String souFilter) {
        this.souFilter = souFilter;
    }

    public String getDestTablename() {
        return this.destTablename;
    }

    public void setDestTablename(String destTablename) {
        this.destTablename = destTablename;
    }

    public String getDestPartKind() {
        return this.destPartKind;
    }

    public void setDestPartKind(String destPartKind) {
        this.destPartKind = destPartKind;
    }

    public String getFlag() {
        return this.flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getParamSou() {
        return this.paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public String getBupdate() {
        return this.bupdate;
    }

    public void setBupdate(String bupdate) {
        this.bupdate = bupdate;
    }

    public String getBcreate() {
        return this.bcreate;
    }

    public void setBcreate(String bcreate) {
        this.bcreate = bcreate;
    }

    public Long getRetryCnt() {
        return this.retryCnt;
    }

    public void setRetryCnt(Long retryCnt) {
        this.retryCnt = retryCnt;
    }

    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return this.endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Long getRuntime() {
        return this.runtime;
    }

    public void setRuntime(Long runtime) {
        this.runtime = runtime;
    }

    public String getTid() {
        return this.tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getSouSplit() {
        return this.souSplit;
    }

    public void setSouSplit(String souSplit) {
        this.souSplit = souSplit;
    }

    public String getRemark() {
        return this.remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getEtlKind() {
        return this.etlKind;
    }

    public void setEtlKind(String etlKind) {
        this.etlKind = etlKind;
    }

    public String getBpreview() {
        return this.bpreview;
    }

    public void setBpreview(String bpreview) {
        this.bpreview = bpreview;
    }

    public Long getRealtimeInterval() {
        return this.realtimeInterval;
    }

    public void setRealtimeInterval(Long realtimeInterval) {
        this.realtimeInterval = realtimeInterval;
    }

    public String getRealtimeTaskgroup() {
        return this.realtimeTaskgroup;
    }

    public void setRealtimeTaskgroup(String realtimeTaskgroup) {
        this.realtimeTaskgroup = realtimeTaskgroup;
    }

    public String getRealtimeSouOwner() {
        return this.realtimeSouOwner;
    }

    public void setRealtimeSouOwner(String realtimeSouOwner) {
        this.realtimeSouOwner = realtimeSouOwner;
    }

    public String getRealtimeFixed() {
        return this.realtimeFixed;
    }

    public void setRealtimeFixed(String realtimeFixed) {
        this.realtimeFixed = realtimeFixed;
    }

    public String getRealtimeSouFilter() {
        return this.realtimeSouFilter;
    }

    public void setRealtimeSouFilter(String realtimeSouFilter) {
        this.realtimeSouFilter = realtimeSouFilter;
    }

    public String getRealtimeIntervalRange() {
        return this.realtimeIntervalRange;
    }

    public void setRealtimeIntervalRange(String realtimeIntervalRange) {
        this.realtimeIntervalRange = realtimeIntervalRange;
    }

    public String getAfterRetryFixed() {
        return this.afterRetryFixed;
    }

    public void setAfterRetryFixed(String afterRetryFixed) {
        this.afterRetryFixed = afterRetryFixed;
    }

    public String getAfterRetryPntype() {
        return this.afterRetryPntype;
    }

    public void setAfterRetryPntype(String afterRetryPntype) {
        this.afterRetryPntype = afterRetryPntype;
    }

    public String getBtdh() {
        return this.btdh;
    }

    public void setBtdh(String btdh) {
        this.btdh = btdh;
    }

    public Long getRuntimeAdd() {
        return this.runtimeAdd;
    }

    public void setRuntimeAdd(Long runtimeAdd) {
        this.runtimeAdd = runtimeAdd;
    }

}