package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_DATE", schema = "STG01", catalog = "")
public class VwImpDateEntity {
    @Basic
    @Column(name = "DT", nullable = true, precision = 0)
    private BigInteger dt;
    @Basic
    @Column(name = "DT_FULL", nullable = true, length = 2000)
    private String dtFull;

    public BigInteger getDt() {
        return dt;
    }

    public void setDt(BigInteger dt) {
        this.dt = dt;
    }

    public String getDtFull() {
        return dtFull;
    }

    public void setDtFull(String dtFull) {
        this.dtFull = dtFull;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpDateEntity that = (VwImpDateEntity) o;
        return Objects.equals(dt, that.dt) && Objects.equals(dtFull, that.dtFull);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dt, dtFull);
    }
}
