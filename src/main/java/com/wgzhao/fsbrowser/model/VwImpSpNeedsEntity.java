package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SP_NEEDS", schema = "STG01", catalog = "")
public class VwImpSpNeedsEntity {
    @Basic
    @Column(name = "SP_ID", nullable = false, length = 32)
    private String spId;
    @Basic
    @Column(name = "FLAG", nullable = true, length = 1)
    private String flag;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "NEEDS", nullable = true, length = 64)
    private String needs;
    @Basic
    @Column(name = "NEEDS_FLAG", nullable = true, length = 1)
    private String needsFlag;
    @Basic
    @Column(name = "NEEDS_END_TIME", nullable = true)
    private Date needsEndTime;

    public String getSpId() {
        return spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
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

    public String getNeeds() {
        return needs;
    }

    public void setNeeds(String needs) {
        this.needs = needs;
    }

    public String getNeedsFlag() {
        return needsFlag;
    }

    public void setNeedsFlag(String needsFlag) {
        this.needsFlag = needsFlag;
    }

    public Date getNeedsEndTime() {
        return needsEndTime;
    }

    public void setNeedsEndTime(Date needsEndTime) {
        this.needsEndTime = needsEndTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpSpNeedsEntity that = (VwImpSpNeedsEntity) o;
        return Objects.equals(spId, that.spId) && Objects.equals(flag, that.flag) && Objects.equals(startTime, that.startTime) && Objects.equals(needs, that.needs) && Objects.equals(needsFlag, that.needsFlag) && Objects.equals(needsEndTime, that.needsEndTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spId, flag, startTime, needs, needsFlag, needsEndTime);
    }
}
