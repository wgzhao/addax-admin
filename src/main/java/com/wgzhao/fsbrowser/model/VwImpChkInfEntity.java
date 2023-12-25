package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_CHK_INF", schema = "STG01", catalog = "")
public class VwImpChkInfEntity {
    @Basic
    @Column(name = "ENGINE", nullable = true, length = 6)
    private String engine;
    @Basic
    @Column(name = "CHK_IDX", nullable = true, length = 255)
    private String chkIdx;
    @Basic
    @Column(name = "CHK_SENDTYPE", nullable = true, length = 4000)
    private String chkSendtype;
    @Basic
    @Column(name = "CHK_MOBILE", nullable = true, length = 4000)
    private String chkMobile;
    @Basic
    @Column(name = "BPNTYPE", nullable = true, precision = 0)
    private BigInteger bpntype;
    @Basic
    @Column(name = "CHK_KIND", nullable = true, length = 4000)
    private String chkKind;
    @Basic
    @Column(name = "CHK_SQL", nullable = true, length = 4000)
    private String chkSql;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getChkIdx() {
        return chkIdx;
    }

    public void setChkIdx(String chkIdx) {
        this.chkIdx = chkIdx;
    }

    public String getChkSendtype() {
        return chkSendtype;
    }

    public void setChkSendtype(String chkSendtype) {
        this.chkSendtype = chkSendtype;
    }

    public String getChkMobile() {
        return chkMobile;
    }

    public void setChkMobile(String chkMobile) {
        this.chkMobile = chkMobile;
    }

    public BigInteger getBpntype() {
        return bpntype;
    }

    public void setBpntype(BigInteger bpntype) {
        this.bpntype = bpntype;
    }

    public String getChkKind() {
        return chkKind;
    }

    public void setChkKind(String chkKind) {
        this.chkKind = chkKind;
    }

    public String getChkSql() {
        return chkSql;
    }

    public void setChkSql(String chkSql) {
        this.chkSql = chkSql;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpChkInfEntity that = (VwImpChkInfEntity) o;
        return Objects.equals(engine, that.engine) && Objects.equals(chkIdx, that.chkIdx) && Objects.equals(chkSendtype, that.chkSendtype) && Objects.equals(chkMobile, that.chkMobile) && Objects.equals(bpntype, that.bpntype) && Objects.equals(chkKind, that.chkKind) && Objects.equals(chkSql, that.chkSql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(engine, chkIdx, chkSendtype, chkMobile, bpntype, chkKind, chkSql);
    }
}
