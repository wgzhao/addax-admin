package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL_OVERPREC", schema = "STG01", catalog = "")
public class VwImpEtlOverprecEntity {
    @Basic
    @Column(name = "SYSNAME", nullable = true, length = 275)
    private String sysname;
    @Basic
    @Column(name = "DB_START", nullable = true, length = 32)
    private String dbStart;
    @Basic
    @Column(name = "DB_START_DT", nullable = true, length = 2000)
    private String dbStartDt;
    @Basic
    @Column(name = "TOTAL_CNT", nullable = true, precision = 0)
    private BigInteger totalCnt;
    @Basic
    @Column(name = "OVER_CNT", nullable = true, precision = 0)
    private BigInteger overCnt;
    @Basic
    @Column(name = "OVER_PREC", nullable = true, precision = 0)
    private BigInteger overPrec;
    @Basic
    @Column(name = "RUN_CNT", nullable = true, precision = 0)
    private BigInteger runCnt;
    @Basic
    @Column(name = "ERR_CNT", nullable = true, precision = 0)
    private BigInteger errCnt;
    @Basic
    @Column(name = "NO_CNT", nullable = true, precision = 0)
    private BigInteger noCnt;
    @Basic
    @Column(name = "WAIT_CNT", nullable = true, precision = 0)
    private BigInteger waitCnt;
    @Basic
    @Column(name = "START_TIME_LTD", nullable = true)
    private Date startTimeLtd;
    @Basic
    @Column(name = "END_TIME_LTD", nullable = true)
    private Date endTimeLtd;
    @Basic
    @Column(name = "START_TIME_TD", nullable = true)
    private Date startTimeTd;
    @Basic
    @Column(name = "END_TIME_TD", nullable = true)
    private Date endTimeTd;
    @Basic
    @Column(name = "START_TIME_R", nullable = true)
    private Date startTimeR;
    @Basic
    @Column(name = "END_TIME_R", nullable = true)
    private Date endTimeR;
    @Basic
    @Column(name = "RUNTIME_LTD", nullable = true, precision = 0)
    private BigInteger runtimeLtd;
    @Basic
    @Column(name = "RUNTIME_TD", nullable = true, precision = 0)
    private BigInteger runtimeTd;
    @Basic
    @Column(name = "RUNTIME_R", nullable = true, precision = 0)
    private BigInteger runtimeR;

    public String getSysname() {
        return sysname;
    }

    public void setSysname(String sysname) {
        this.sysname = sysname;
    }

    public String getDbStart() {
        return dbStart;
    }

    public void setDbStart(String dbStart) {
        this.dbStart = dbStart;
    }

    public String getDbStartDt() {
        return dbStartDt;
    }

    public void setDbStartDt(String dbStartDt) {
        this.dbStartDt = dbStartDt;
    }

    public BigInteger getTotalCnt() {
        return totalCnt;
    }

    public void setTotalCnt(BigInteger totalCnt) {
        this.totalCnt = totalCnt;
    }

    public BigInteger getOverCnt() {
        return overCnt;
    }

    public void setOverCnt(BigInteger overCnt) {
        this.overCnt = overCnt;
    }

    public BigInteger getOverPrec() {
        return overPrec;
    }

    public void setOverPrec(BigInteger overPrec) {
        this.overPrec = overPrec;
    }

    public BigInteger getRunCnt() {
        return runCnt;
    }

    public void setRunCnt(BigInteger runCnt) {
        this.runCnt = runCnt;
    }

    public BigInteger getErrCnt() {
        return errCnt;
    }

    public void setErrCnt(BigInteger errCnt) {
        this.errCnt = errCnt;
    }

    public BigInteger getNoCnt() {
        return noCnt;
    }

    public void setNoCnt(BigInteger noCnt) {
        this.noCnt = noCnt;
    }

    public BigInteger getWaitCnt() {
        return waitCnt;
    }

    public void setWaitCnt(BigInteger waitCnt) {
        this.waitCnt = waitCnt;
    }

    public Date getStartTimeLtd() {
        return startTimeLtd;
    }

    public void setStartTimeLtd(Date startTimeLtd) {
        this.startTimeLtd = startTimeLtd;
    }

    public Date getEndTimeLtd() {
        return endTimeLtd;
    }

    public void setEndTimeLtd(Date endTimeLtd) {
        this.endTimeLtd = endTimeLtd;
    }

    public Date getStartTimeTd() {
        return startTimeTd;
    }

    public void setStartTimeTd(Date startTimeTd) {
        this.startTimeTd = startTimeTd;
    }

    public Date getEndTimeTd() {
        return endTimeTd;
    }

    public void setEndTimeTd(Date endTimeTd) {
        this.endTimeTd = endTimeTd;
    }

    public Date getStartTimeR() {
        return startTimeR;
    }

    public void setStartTimeR(Date startTimeR) {
        this.startTimeR = startTimeR;
    }

    public Date getEndTimeR() {
        return endTimeR;
    }

    public void setEndTimeR(Date endTimeR) {
        this.endTimeR = endTimeR;
    }

    public BigInteger getRuntimeLtd() {
        return runtimeLtd;
    }

    public void setRuntimeLtd(BigInteger runtimeLtd) {
        this.runtimeLtd = runtimeLtd;
    }

    public BigInteger getRuntimeTd() {
        return runtimeTd;
    }

    public void setRuntimeTd(BigInteger runtimeTd) {
        this.runtimeTd = runtimeTd;
    }

    public BigInteger getRuntimeR() {
        return runtimeR;
    }

    public void setRuntimeR(BigInteger runtimeR) {
        this.runtimeR = runtimeR;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpEtlOverprecEntity that = (VwImpEtlOverprecEntity) o;
        return Objects.equals(sysname, that.sysname) && Objects.equals(dbStart, that.dbStart) && Objects.equals(dbStartDt, that.dbStartDt) && Objects.equals(totalCnt, that.totalCnt) && Objects.equals(overCnt, that.overCnt) && Objects.equals(overPrec, that.overPrec) && Objects.equals(runCnt, that.runCnt) && Objects.equals(errCnt, that.errCnt) && Objects.equals(noCnt, that.noCnt) && Objects.equals(waitCnt, that.waitCnt) && Objects.equals(startTimeLtd, that.startTimeLtd) && Objects.equals(endTimeLtd, that.endTimeLtd) && Objects.equals(startTimeTd, that.startTimeTd) && Objects.equals(endTimeTd, that.endTimeTd) && Objects.equals(startTimeR, that.startTimeR) && Objects.equals(endTimeR, that.endTimeR) && Objects.equals(runtimeLtd, that.runtimeLtd) && Objects.equals(runtimeTd, that.runtimeTd) && Objects.equals(runtimeR, that.runtimeR);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sysname, dbStart, dbStartDt, totalCnt, overCnt, overPrec, runCnt, errCnt, noCnt, waitCnt, startTimeLtd, endTimeLtd, startTimeTd, endTimeTd, startTimeR, endTimeR, runtimeLtd, runtimeTd, runtimeR);
    }
}
