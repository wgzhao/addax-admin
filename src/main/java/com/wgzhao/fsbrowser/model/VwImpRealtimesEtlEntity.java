package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_REALTIMES_ETL", schema = "STG01", catalog = "")
public class VwImpRealtimesEtlEntity {
    @Basic
    @Column(name = "LAST_TIMES", nullable = true, length = 2000)
    private String lastTimes;
    @Basic
    @Column(name = "NEXT_TIMES", nullable = true, length = 2000)
    private String nextTimes;
    @Basic
    @Column(name = "SPNAME", nullable = true, length = 100)
    private String spname;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "END_TIME", nullable = true)
    private Date endTime;
    @Basic
    @Column(name = "FLAG", nullable = true, length = 1)
    private String flag;

    public String getLastTimes() {
        return lastTimes;
    }

    public void setLastTimes(String lastTimes) {
        this.lastTimes = lastTimes;
    }

    public String getNextTimes() {
        return nextTimes;
    }

    public void setNextTimes(String nextTimes) {
        this.nextTimes = nextTimes;
    }

    public String getSpname() {
        return spname;
    }

    public void setSpname(String spname) {
        this.spname = spname;
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

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpRealtimesEtlEntity that = (VwImpRealtimesEtlEntity) o;
        return Objects.equals(lastTimes, that.lastTimes) && Objects.equals(nextTimes, that.nextTimes) && Objects.equals(spname, that.spname) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(flag, that.flag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastTimes, nextTimes, spname, startTime, endTime, flag);
    }
}
