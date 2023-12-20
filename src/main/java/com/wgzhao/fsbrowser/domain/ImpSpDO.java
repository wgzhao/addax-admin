package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * HADOOP_SP的配置主表实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpSpDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SP用户
     */
    private String spOwner;

    /**
     * SP名称
     */
    private String spName;

    /**
     * SP的主键，自动生成，唯一性
     */
    private String spId;

    /**
     * 运行状态
     */
    private String flag;

    /**
     * 运行频率，周期(D天,W周,M月,Q季度,Y年度)
     */
    private String runFreq;

    /**
     * 运行开始时间
     */
    private Date startTime;

    /**
     * 运行结束时间
     */
    private Date endTime;

    /**
     * 错误重试次数
     */
    private Long retryCnt;

    /**
     * 任务运行时间，用来并发排序
     */
    private Long runtime;

    /**
     * 所属任务组，用于调起后续工作流
     */
    private String taskGroup;

    /**
     * 参数文件组（L昨日，C当日，N下日）
     */
    private String paramSou;

    /**
     * 备注信息
     */
    private String remark;

    /**
     * 前置采集任务组(专用了等待实时的采集任务组)
     */
    private String realtimeTaskgroup;

    /**
     * 计划类型
     */
    private String pnType;

    /**
     * 计划定点时间
     */
    private String pnFixed;

    /**
     * 计划间隔时间
     */
    private Long pnInterval;

    /**
     * 计划间隔时间范围
     */
    private String pnRange;

    public String getSpOwner() {
        return this.spOwner;
    }

    public void setSpOwner(String spOwner) {
        this.spOwner = spOwner;
    }

    public String getSpName() {
        return this.spName;
    }

    public void setSpName(String spName) {
        this.spName = spName;
    }

    public String getSpId() {
        return this.spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public String getFlag() {
        return this.flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getRunFreq() {
        return this.runFreq;
    }

    public void setRunFreq(String runFreq) {
        this.runFreq = runFreq;
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

    public Long getRetryCnt() {
        return this.retryCnt;
    }

    public void setRetryCnt(Long retryCnt) {
        this.retryCnt = retryCnt;
    }

    public Long getRuntime() {
        return this.runtime;
    }

    public void setRuntime(Long runtime) {
        this.runtime = runtime;
    }

    public String getTaskGroup() {
        return this.taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getParamSou() {
        return this.paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public String getRemark() {
        return this.remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRealtimeTaskgroup() {
        return this.realtimeTaskgroup;
    }

    public void setRealtimeTaskgroup(String realtimeTaskgroup) {
        this.realtimeTaskgroup = realtimeTaskgroup;
    }

    public String getPnType() {
        return this.pnType;
    }

    public void setPnType(String pnType) {
        this.pnType = pnType;
    }

    public String getPnFixed() {
        return this.pnFixed;
    }

    public void setPnFixed(String pnFixed) {
        this.pnFixed = pnFixed;
    }

    public Long getPnInterval() {
        return this.pnInterval;
    }

    public void setPnInterval(Long pnInterval) {
        this.pnInterval = pnInterval;
    }

    public String getPnRange() {
        return this.pnRange;
    }

    public void setPnRange(String pnRange) {
        this.pnRange = pnRange;
    }

}