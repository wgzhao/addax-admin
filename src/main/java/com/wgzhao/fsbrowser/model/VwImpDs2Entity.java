package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_DS2", schema = "STG01", catalog = "")
public class VwImpDs2Entity {
    @Basic
    @Column(name = "DS_ID", nullable = false, length = 32)
    private String dsId;
    @Basic
    @Column(name = "DS_NAME", nullable = true, length = 339)
    private String dsName;
    @Basic
    @Column(name = "DEST_SYSID", nullable = false, length = 32)
    private String destSysid;
    @Basic
    @Column(name = "TASK_GROUP", nullable = false, length = 32)
    private String taskGroup;
    @Basic
    @Column(name = "PARAM_SOU", nullable = true, length = 1)
    private String paramSou;
    @Basic
    @Column(name = "RETRY_CNT", nullable = true, precision = 0)
    private BigInteger retryCnt;
    @Basic
    @Column(name = "RUN_FREQ", nullable = true, length = 3)
    private String runFreq;
    @Basic
    @Column(name = "BVALID", nullable = true, precision = 0)
    private BigInteger bvalid;
    @Basic
    @Column(name = "FLAG", nullable = true, length = 1)
    private String flag;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "END_TIME", nullable = true)
    private Date endTime;
    @Basic
    @Column(name = "RUNTIME", nullable = true, precision = 0)
    private Integer runtime;
    @Basic
    @Column(name = "BRUN", nullable = true, precision = 0)
    private BigInteger brun;
    @Basic
    @Column(name = "BDELAY", nullable = true, precision = 0)
    private BigInteger bdelay;
    @Basic
    @Column(name = "BFREQ", nullable = true, precision = 0)
    private BigInteger bfreq;
    @Basic
    @Column(name = "BPLAN", nullable = true, precision = 0)
    private BigInteger bplan;
    @Basic
    @Column(name = "PRE_SH", nullable = true)
    private String preSh;
    @Basic
    @Column(name = "POST_SH", nullable = true)
    private String postSh;
    @Basic
    @Column(name = "PRE_SQL", nullable = true)
    private String preSql;
    @Basic
    @Column(name = "POST_SQL", nullable = true)
    private String postSql;
    @Basic
    @Column(name = "INIT_RDS", nullable = true, length = 432)
    private String initRds;

    public String getDsId() {
        return dsId;
    }

    public void setDsId(String dsId) {
        this.dsId = dsId;
    }

    public String getDsName() {
        return dsName;
    }

    public void setDsName(String dsName) {
        this.dsName = dsName;
    }

    public String getDestSysid() {
        return destSysid;
    }

    public void setDestSysid(String destSysid) {
        this.destSysid = destSysid;
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

    public BigInteger getRetryCnt() {
        return retryCnt;
    }

    public void setRetryCnt(BigInteger retryCnt) {
        this.retryCnt = retryCnt;
    }

    public String getRunFreq() {
        return runFreq;
    }

    public void setRunFreq(String runFreq) {
        this.runFreq = runFreq;
    }

    public BigInteger getBvalid() {
        return bvalid;
    }

    public void setBvalid(BigInteger bvalid) {
        this.bvalid = bvalid;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
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

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public BigInteger getBrun() {
        return brun;
    }

    public void setBrun(BigInteger brun) {
        this.brun = brun;
    }

    public BigInteger getBdelay() {
        return bdelay;
    }

    public void setBdelay(BigInteger bdelay) {
        this.bdelay = bdelay;
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

    public String getPreSh() {
        return preSh;
    }

    public void setPreSh(String preSh) {
        this.preSh = preSh;
    }

    public String getPostSh() {
        return postSh;
    }

    public void setPostSh(String postSh) {
        this.postSh = postSh;
    }

    public String getPreSql() {
        return preSql;
    }

    public void setPreSql(String preSql) {
        this.preSql = preSql;
    }

    public String getPostSql() {
        return postSql;
    }

    public void setPostSql(String postSql) {
        this.postSql = postSql;
    }

    public String getInitRds() {
        return initRds;
    }

    public void setInitRds(String initRds) {
        this.initRds = initRds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpDs2Entity that = (VwImpDs2Entity) o;
        return Objects.equals(dsId, that.dsId) && Objects.equals(dsName, that.dsName) && Objects.equals(destSysid, that.destSysid) && Objects.equals(taskGroup, that.taskGroup) && Objects.equals(paramSou, that.paramSou) && Objects.equals(retryCnt, that.retryCnt) && Objects.equals(runFreq, that.runFreq) && Objects.equals(bvalid, that.bvalid) && Objects.equals(flag, that.flag) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(runtime, that.runtime) && Objects.equals(brun, that.brun) && Objects.equals(bdelay, that.bdelay) && Objects.equals(bfreq, that.bfreq) && Objects.equals(bplan, that.bplan) && Objects.equals(preSh, that.preSh) && Objects.equals(postSh, that.postSh) && Objects.equals(preSql, that.preSql) && Objects.equals(postSql, that.postSql) && Objects.equals(initRds, that.initRds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dsId, dsName, destSysid, taskGroup, paramSou, retryCnt, runFreq, bvalid, flag, startTime, endTime, runtime, brun, bdelay, bfreq, bplan, preSh, postSh, preSql, postSql, initRds);
    }
}
