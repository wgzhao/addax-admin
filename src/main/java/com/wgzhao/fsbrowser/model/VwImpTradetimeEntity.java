package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TRADETIME", schema = "STG01", catalog = "")
public class VwImpTradetimeEntity {
    @Basic
    @Column(name = "TD", nullable = true)
    private Date td;
    @Basic
    @Column(name = "NTD", nullable = true)
    private Date ntd;

    public Date getTd() {
        return td;
    }

    public void setTd(Date td) {
        this.td = td;
    }

    public Date getNtd() {
        return ntd;
    }

    public void setNtd(Date ntd) {
        this.ntd = ntd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpTradetimeEntity that = (VwImpTradetimeEntity) o;
        return Objects.equals(td, that.td) && Objects.equals(ntd, that.ntd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(td, ntd);
    }
}
