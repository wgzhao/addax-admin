package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_DS2_NEEDS", schema = "STG01", catalog = "")
public class VwImpDs2NeedsEntity {
    @Basic
    @Column(name = "DS_ID", nullable = true, length = 32)
    private String dsId;
    @Basic
    @Column(name = "TASK_GROUP", nullable = true, length = 32)
    private String taskGroup;
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
    @Column(name = "BMULTI", nullable = true, precision = 0)
    private BigInteger bmulti;
    @Basic
    @Column(name = "NEEDS", nullable = true, length = 128)
    private String needs;
    @Basic
    @Column(name = "BOVER", nullable = true, precision = 0)
    private BigInteger bover;

    public String getDsId() {
        return dsId;
    }

    public void setDsId(String dsId) {
        this.dsId = dsId;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
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

    public BigInteger getBmulti() {
        return bmulti;
    }

    public void setBmulti(BigInteger bmulti) {
        this.bmulti = bmulti;
    }

    public String getNeeds() {
        return needs;
    }

    public void setNeeds(String needs) {
        this.needs = needs;
    }

    public BigInteger getBover() {
        return bover;
    }

    public void setBover(BigInteger bover) {
        this.bover = bover;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpDs2NeedsEntity that = (VwImpDs2NeedsEntity) o;
        return Objects.equals(dsId, that.dsId) && Objects.equals(taskGroup, that.taskGroup) && Objects.equals(flag, that.flag) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(bmulti, that.bmulti) && Objects.equals(needs, that.needs) && Objects.equals(bover, that.bover);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dsId, taskGroup, flag, startTime, endTime, bmulti, needs, bover);
    }
}
