package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TASKGROUP_DETAIL", schema = "STG01", catalog = "")
public class VwImpTaskgroupDetailEntity {
    @Basic
    @Column(name = "TASK_GROUP", nullable = true, length = 4000)
    private String taskGroup;
    @Basic
    @Column(name = "KIND", nullable = true, length = 12)
    private String kind;
    @Basic
    @Column(name = "ALLCNT", nullable = true, precision = 0)
    private BigInteger allcnt;
    @Basic
    @Column(name = "YCNT", nullable = true, precision = 0)
    private BigInteger ycnt;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "END_TIME", nullable = true)
    private Date endTime;
    @Basic
    @Column(name = "RUNTIME", nullable = true, precision = 0)
    private BigInteger runtime;
    @Basic
    @Column(name = "RCNT", nullable = true, precision = 0)
    private BigInteger rcnt;
    @Basic
    @Column(name = "START_TIME_R", nullable = true)
    private Date startTimeR;
    @Basic
    @Column(name = "NCNT", nullable = true, precision = 0)
    private BigInteger ncnt;
    @Basic
    @Column(name = "ECNT", nullable = true, precision = 0)
    private BigInteger ecnt;
    @Basic
    @Column(name = "PREC", nullable = true, precision = 0)
    private BigInteger prec;
    @Basic
    @Column(name = "KIND2", nullable = true, length = 12)
    private String kind2;
    @Basic
    @Column(name = "TASK_GROUP2", nullable = true, length = 128)
    private String taskGroup2;
    @Basic
    @Column(name = "DS_NAME", nullable = true, length = 511)
    private String dsName;
    @Basic
    @Column(name = "FLAG2", nullable = true, length = 1)
    private String flag2;
    @Basic
    @Column(name = "START_TIME2", nullable = true)
    private Date startTime2;
    @Basic
    @Column(name = "END_TIME2", nullable = true)
    private Date endTime2;
    @Basic
    @Column(name = "BFLAG", nullable = true, length = 3)
    private String bflag;
    @Basic
    @Column(name = "FLAG_TIME", nullable = true)
    private Date flagTime;
    @Basic
    @Column(name = "ERRMSG", nullable = true, length = 48)
    private String errmsg;

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public BigInteger getAllcnt() {
        return allcnt;
    }

    public void setAllcnt(BigInteger allcnt) {
        this.allcnt = allcnt;
    }

    public BigInteger getYcnt() {
        return ycnt;
    }

    public void setYcnt(BigInteger ycnt) {
        this.ycnt = ycnt;
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

    public BigInteger getRuntime() {
        return runtime;
    }

    public void setRuntime(BigInteger runtime) {
        this.runtime = runtime;
    }

    public BigInteger getRcnt() {
        return rcnt;
    }

    public void setRcnt(BigInteger rcnt) {
        this.rcnt = rcnt;
    }

    public Date getStartTimeR() {
        return startTimeR;
    }

    public void setStartTimeR(Date startTimeR) {
        this.startTimeR = startTimeR;
    }

    public BigInteger getNcnt() {
        return ncnt;
    }

    public void setNcnt(BigInteger ncnt) {
        this.ncnt = ncnt;
    }

    public BigInteger getEcnt() {
        return ecnt;
    }

    public void setEcnt(BigInteger ecnt) {
        this.ecnt = ecnt;
    }

    public BigInteger getPrec() {
        return prec;
    }

    public void setPrec(BigInteger prec) {
        this.prec = prec;
    }

    public String getKind2() {
        return kind2;
    }

    public void setKind2(String kind2) {
        this.kind2 = kind2;
    }

    public String getTaskGroup2() {
        return taskGroup2;
    }

    public void setTaskGroup2(String taskGroup2) {
        this.taskGroup2 = taskGroup2;
    }

    public String getDsName() {
        return dsName;
    }

    public void setDsName(String dsName) {
        this.dsName = dsName;
    }

    public String getFlag2() {
        return flag2;
    }

    public void setFlag2(String flag2) {
        this.flag2 = flag2;
    }

    public Date getStartTime2() {
        return startTime2;
    }

    public void setStartTime2(Date startTime2) {
        this.startTime2 = startTime2;
    }

    public Date getEndTime2() {
        return endTime2;
    }

    public void setEndTime2(Date endTime2) {
        this.endTime2 = endTime2;
    }

    public String getBflag() {
        return bflag;
    }

    public void setBflag(String bflag) {
        this.bflag = bflag;
    }

    public Date getFlagTime() {
        return flagTime;
    }

    public void setFlagTime(Date flagTime) {
        this.flagTime = flagTime;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public void setErrmsg(String errmsg) {
        this.errmsg = errmsg;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpTaskgroupDetailEntity that = (VwImpTaskgroupDetailEntity) o;
        return Objects.equals(taskGroup, that.taskGroup) && Objects.equals(kind, that.kind) && Objects.equals(allcnt, that.allcnt) && Objects.equals(ycnt, that.ycnt) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(runtime, that.runtime) && Objects.equals(rcnt, that.rcnt) && Objects.equals(startTimeR, that.startTimeR) && Objects.equals(ncnt, that.ncnt) && Objects.equals(ecnt, that.ecnt) && Objects.equals(prec, that.prec) && Objects.equals(kind2, that.kind2) && Objects.equals(taskGroup2, that.taskGroup2) && Objects.equals(dsName, that.dsName) && Objects.equals(flag2, that.flag2) && Objects.equals(startTime2, that.startTime2) && Objects.equals(endTime2, that.endTime2) && Objects.equals(bflag, that.bflag) && Objects.equals(flagTime, that.flagTime) && Objects.equals(errmsg, that.errmsg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskGroup, kind, allcnt, ycnt, startTime, endTime, runtime, rcnt, startTimeR, ncnt, ecnt, prec, kind2, taskGroup2, dsName, flag2, startTime2, endTime2, bflag, flagTime, errmsg);
    }
}
