package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_PLAN", schema = "STG01", catalog = "")
public class VwImpPlanEntity {
    @Basic
    @Column(name = "PN_ID", nullable = false, length = 32)
    private String pnId;
    @Basic
    @Column(name = "PN_TYPE", nullable = true, length = 1)
    private String pnType;
    @Basic
    @Column(name = "PN_TYPE_NAME", nullable = true, length = 4000)
    private String pnTypeName;
    @Basic
    @Column(name = "PN_FIXED", nullable = true, length = 200)
    private String pnFixed;
    @Basic
    @Column(name = "DT_FULL", nullable = true, length = 2000)
    private String dtFull;
    @Basic
    @Column(name = "PN_INTERVAL", nullable = true, precision = 0)
    private Short pnInterval;
    @Basic
    @Column(name = "PN_RANGE", nullable = true, length = 32)
    private String pnRange;
    @Basic
    @Column(name = "SPNAME", nullable = true, length = 4000)
    private String spname;
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
    @Column(name = "BPNTYPE", nullable = true, precision = 0)
    private BigInteger bpntype;

    public String getPnId() {
        return pnId;
    }

    public void setPnId(String pnId) {
        this.pnId = pnId;
    }

    public String getPnType() {
        return pnType;
    }

    public void setPnType(String pnType) {
        this.pnType = pnType;
    }

    public String getPnTypeName() {
        return pnTypeName;
    }

    public void setPnTypeName(String pnTypeName) {
        this.pnTypeName = pnTypeName;
    }

    public String getPnFixed() {
        return pnFixed;
    }

    public void setPnFixed(String pnFixed) {
        this.pnFixed = pnFixed;
    }

    public String getDtFull() {
        return dtFull;
    }

    public void setDtFull(String dtFull) {
        this.dtFull = dtFull;
    }

    public Short getPnInterval() {
        return pnInterval;
    }

    public void setPnInterval(Short pnInterval) {
        this.pnInterval = pnInterval;
    }

    public String getPnRange() {
        return pnRange;
    }

    public void setPnRange(String pnRange) {
        this.pnRange = pnRange;
    }

    public String getSpname() {
        return spname;
    }

    public void setSpname(String spname) {
        this.spname = spname;
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

    public BigInteger getBpntype() {
        return bpntype;
    }

    public void setBpntype(BigInteger bpntype) {
        this.bpntype = bpntype;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpPlanEntity that = (VwImpPlanEntity) o;
        return Objects.equals(pnId, that.pnId) && Objects.equals(pnType, that.pnType) && Objects.equals(pnTypeName, that.pnTypeName) && Objects.equals(pnFixed, that.pnFixed) && Objects.equals(dtFull, that.dtFull) && Objects.equals(pnInterval, that.pnInterval) && Objects.equals(pnRange, that.pnRange) && Objects.equals(spname, that.spname) && Objects.equals(flag, that.flag) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(runtime, that.runtime) && Objects.equals(brun, that.brun) && Objects.equals(bpntype, that.bpntype);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pnId, pnType, pnTypeName, pnFixed, dtFull, pnInterval, pnRange, spname, flag, startTime, endTime, runtime, brun, bpntype);
    }
}
