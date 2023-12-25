package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SP", schema = "STG01", catalog = "")
public class VwImpSpEntity {
    @Basic
    @Column(name = "SP_OWNER", nullable = true, length = 255)
    private String spOwner;
    @Basic
    @Column(name = "SP_NAME", nullable = true, length = 255)
    private String spName;
    @Basic
    @Column(name = "SP_ID", nullable = false, length = 32)
    private String spId;
    @Basic
    @Column(name = "RID", nullable = true)
    private Object rid;
    @Basic
    @Column(name = "FLAG", nullable = true, length = 1)
    private String flag;
    @Basic
    @Column(name = "RUN_FREQ", nullable = true, length = 3)
    private String runFreq;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "END_TIME", nullable = true)
    private Date endTime;
    @Basic
    @Column(name = "RETRY_CNT", nullable = true, precision = 0)
    private Byte retryCnt;
    @Basic
    @Column(name = "RUNTIME", nullable = true, precision = 0)
    private Integer runtime;
    @Basic
    @Column(name = "REALTIME_TASKGROUP", nullable = true, length = 32)
    private String realtimeTaskgroup;
    @Basic
    @Column(name = "NEED_SOU", nullable = true, length = 4000)
    private String needSou;
    @Basic
    @Column(name = "NEED_SP", nullable = true, length = 4000)
    private String needSp;
    @Basic
    @Column(name = "SP_ALLTABS", nullable = true, length = 4000)
    private String spAlltabs;
    @Basic
    @Column(name = "SP_DEST", nullable = true, length = 4000)
    private String spDest;
    @Basic
    @Column(name = "THROUGH_NEED_SOU", nullable = true, length = 4000)
    private String throughNeedSou;
    @Basic
    @Column(name = "THROUGH_NEED_SP", nullable = true)
    private String throughNeedSp;
    @Basic
    @Column(name = "TASK_GROUP", nullable = true, length = 128)
    private String taskGroup;
    @Basic
    @Column(name = "PARAM_SOU", nullable = true, length = 1)
    private String paramSou;
    @Basic
    @Column(name = "REMARK", nullable = true)
    private String remark;
    @Basic
    @Column(name = "SPNAME", nullable = true, length = 511)
    private String spname;
    @Basic
    @Column(name = "BVALID", nullable = true, precision = 0)
    private BigInteger bvalid;
    @Basic
    @Column(name = "BRUN", nullable = true, precision = 0)
    private BigInteger brun;
    @Basic
    @Column(name = "BFREQ", nullable = true, precision = 0)
    private BigInteger bfreq;
    @Basic
    @Column(name = "BPLAN", nullable = true, precision = 0)
    private BigInteger bplan;

    public String getSpOwner() {
        return spOwner;
    }

    public void setSpOwner(String spOwner) {
        this.spOwner = spOwner;
    }

    public String getSpName() {
        return spName;
    }

    public void setSpName(String spName) {
        this.spName = spName;
    }

    public String getSpId() {
        return spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public Object getRid() {
        return rid;
    }

    public void setRid(Object rid) {
        this.rid = rid;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getRunFreq() {
        return runFreq;
    }

    public void setRunFreq(String runFreq) {
        this.runFreq = runFreq;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Byte getRetryCnt() {
        return retryCnt;
    }

    public void setRetryCnt(Byte retryCnt) {
        this.retryCnt = retryCnt;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public String getRealtimeTaskgroup() {
        return realtimeTaskgroup;
    }

    public void setRealtimeTaskgroup(String realtimeTaskgroup) {
        this.realtimeTaskgroup = realtimeTaskgroup;
    }

    public String getNeedSou() {
        return needSou;
    }

    public void setNeedSou(String needSou) {
        this.needSou = needSou;
    }

    public String getNeedSp() {
        return needSp;
    }

    public void setNeedSp(String needSp) {
        this.needSp = needSp;
    }

    public String getSpAlltabs() {
        return spAlltabs;
    }

    public void setSpAlltabs(String spAlltabs) {
        this.spAlltabs = spAlltabs;
    }

    public String getSpDest() {
        return spDest;
    }

    public void setSpDest(String spDest) {
        this.spDest = spDest;
    }

    public String getThroughNeedSou() {
        return throughNeedSou;
    }

    public void setThroughNeedSou(String throughNeedSou) {
        this.throughNeedSou = throughNeedSou;
    }

    public String getThroughNeedSp() {
        return throughNeedSp;
    }

    public void setThroughNeedSp(String throughNeedSp) {
        this.throughNeedSp = throughNeedSp;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getParamSou() {
        return paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSpname() {
        return spname;
    }

    public void setSpname(String spname) {
        this.spname = spname;
    }

    public BigInteger getBvalid() {
        return bvalid;
    }

    public void setBvalid(BigInteger bvalid) {
        this.bvalid = bvalid;
    }

    public BigInteger getBrun() {
        return brun;
    }

    public void setBrun(BigInteger brun) {
        this.brun = brun;
    }

    public BigInteger getBfreq() {
        return bfreq;
    }

    public void setBfreq(BigInteger bfreq) {
        this.bfreq = bfreq;
    }

    public BigInteger getBplan() {
        return bplan;
    }

    public void setBplan(BigInteger bplan) {
        this.bplan = bplan;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpSpEntity that = (VwImpSpEntity) o;
        return Objects.equals(spOwner, that.spOwner) && Objects.equals(spName, that.spName) && Objects.equals(spId, that.spId) && Objects.equals(rid, that.rid) && Objects.equals(flag, that.flag) && Objects.equals(runFreq, that.runFreq) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(retryCnt, that.retryCnt) && Objects.equals(runtime, that.runtime) && Objects.equals(realtimeTaskgroup, that.realtimeTaskgroup) && Objects.equals(needSou, that.needSou) && Objects.equals(needSp, that.needSp) && Objects.equals(spAlltabs, that.spAlltabs) && Objects.equals(spDest, that.spDest) && Objects.equals(throughNeedSou, that.throughNeedSou) && Objects.equals(throughNeedSp, that.throughNeedSp) && Objects.equals(taskGroup, that.taskGroup) && Objects.equals(paramSou, that.paramSou) && Objects.equals(remark, that.remark) && Objects.equals(spname, that.spname) && Objects.equals(bvalid, that.bvalid) && Objects.equals(brun, that.brun) && Objects.equals(bfreq, that.bfreq) && Objects.equals(bplan, that.bplan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spOwner, spName, spId, rid, flag, runFreq, startTime, endTime, retryCnt, runtime, realtimeTaskgroup, needSou, needSp, spAlltabs, spDest, throughNeedSou, throughNeedSp, taskGroup, paramSou, remark, spname, bvalid, brun, bfreq, bplan);
    }
}
