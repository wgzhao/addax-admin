package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_FLAG", schema = "STG01", catalog = "")
public class VwImpFlagEntity {
    @Basic
    @Column(name = "TRADEDATE", nullable = true, precision = 0)
    private Integer tradedate;
    @Basic
    @Column(name = "KIND", nullable = false, length = 32)
    private String kind;
    @Basic
    @Column(name = "FID", nullable = false, length = 32)
    private String fid;
    @Basic
    @Column(name = "FVAL", nullable = true, length = 32)
    private String fval;
    @Basic
    @Column(name = "DW_CLT_DATE", nullable = false)
    private Date dwCltDate;

    public Integer getTradedate() {
        return tradedate;
    }

    public void setTradedate(Integer tradedate) {
        this.tradedate = tradedate;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getFval() {
        return fval;
    }

    public void setFval(String fval) {
        this.fval = fval;
    }

    public Date getDwCltDate() {
        return dwCltDate;
    }

    public void setDwCltDate(Date dwCltDate) {
        this.dwCltDate = dwCltDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpFlagEntity that = (VwImpFlagEntity) o;
        return Objects.equals(tradedate, that.tradedate) && Objects.equals(kind, that.kind) && Objects.equals(fid, that.fid) && Objects.equals(fval, that.fval) && Objects.equals(dwCltDate, that.dwCltDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradedate, kind, fid, fval, dwCltDate);
    }
}
