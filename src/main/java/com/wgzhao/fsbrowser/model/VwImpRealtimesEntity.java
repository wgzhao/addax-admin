package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_REALTIMES", schema = "STG01", catalog = "")
public class VwImpRealtimesEntity {
    @Basic
    @Column(name = "TID", nullable = true, length = 32)
    private String tid;
    @Basic
    @Column(name = "TIMES", nullable = true, length = 2000)
    private String times;

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTimes() {
        return times;
    }

    public void setTimes(String times) {
        this.times = times;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpRealtimesEntity that = (VwImpRealtimesEntity) o;
        return Objects.equals(tid, that.tid) && Objects.equals(times, that.times);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tid, times);
    }
}
