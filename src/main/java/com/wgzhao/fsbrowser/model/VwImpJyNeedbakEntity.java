package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_JY_NEEDBAK", schema = "STG01", catalog = "")
public class VwImpJyNeedbakEntity {
    @Basic
    @Column(name = "TBL", nullable = true, length = 64)
    private String tbl;

    public String getTbl() {
        return tbl;
    }

    public void setTbl(String tbl) {
        this.tbl = tbl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpJyNeedbakEntity that = (VwImpJyNeedbakEntity) o;
        return Objects.equals(tbl, that.tbl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tbl);
    }
}
