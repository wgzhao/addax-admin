package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TBL_HDP", schema = "STG01", catalog = "")
public class VwImpTblHdpEntity {
    @Basic
    @Column(name = "TID", nullable = false, length = 32)
    private String tid;
    @Basic
    @Column(name = "BUPDATE", nullable = true, length = 1)
    private String bupdate;
    @Basic
    @Column(name = "HIVE_OWNER", nullable = true, length = 35)
    private String hiveOwner;
    @Basic
    @Column(name = "HIVE_TABLENAME", nullable = false, length = 64)
    private String hiveTablename;
    @Basic
    @Column(name = "COL_NAME", nullable = true, length = 255)
    private String colName;
    @Basic
    @Column(name = "COL_TYPE_FULL", nullable = true, length = 500)
    private String colTypeFull;
    @Basic
    @Column(name = "COL_TYPE", nullable = true, length = 2000)
    private String colType;
    @Basic
    @Column(name = "COL_PRECISION", nullable = true, precision = 0)
    private BigInteger colPrecision;
    @Basic
    @Column(name = "COL_SCALE", nullable = true, precision = 0)
    private BigInteger colScale;
    @Basic
    @Column(name = "COL_IDX", nullable = true, precision = 0)
    private BigInteger colIdx;
    @Basic
    @Column(name = "TBL_COMMENT", nullable = true, length = 4000)
    private String tblComment;
    @Basic
    @Column(name = "COL_COMMENT", nullable = true, length = 4000)
    private String colComment;
    @Basic
    @Column(name = "CD_ID", nullable = true, precision = 0)
    private BigInteger cdId;

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getBupdate() {
        return bupdate;
    }

    public void setBupdate(String bupdate) {
        this.bupdate = bupdate;
    }

    public String getHiveOwner() {
        return hiveOwner;
    }

    public void setHiveOwner(String hiveOwner) {
        this.hiveOwner = hiveOwner;
    }

    public String getHiveTablename() {
        return hiveTablename;
    }

    public void setHiveTablename(String hiveTablename) {
        this.hiveTablename = hiveTablename;
    }

    public String getColName() {
        return colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getColTypeFull() {
        return colTypeFull;
    }

    public void setColTypeFull(String colTypeFull) {
        this.colTypeFull = colTypeFull;
    }

    public String getColType() {
        return colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public BigInteger getColPrecision() {
        return colPrecision;
    }

    public void setColPrecision(BigInteger colPrecision) {
        this.colPrecision = colPrecision;
    }

    public BigInteger getColScale() {
        return colScale;
    }

    public void setColScale(BigInteger colScale) {
        this.colScale = colScale;
    }

    public BigInteger getColIdx() {
        return colIdx;
    }

    public void setColIdx(BigInteger colIdx) {
        this.colIdx = colIdx;
    }

    public String getTblComment() {
        return tblComment;
    }

    public void setTblComment(String tblComment) {
        this.tblComment = tblComment;
    }

    public String getColComment() {
        return colComment;
    }

    public void setColComment(String colComment) {
        this.colComment = colComment;
    }

    public BigInteger getCdId() {
        return cdId;
    }

    public void setCdId(BigInteger cdId) {
        this.cdId = cdId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpTblHdpEntity that = (VwImpTblHdpEntity) o;
        return Objects.equals(tid, that.tid) && Objects.equals(bupdate, that.bupdate) && Objects.equals(hiveOwner, that.hiveOwner) && Objects.equals(hiveTablename, that.hiveTablename) && Objects.equals(colName, that.colName) && Objects.equals(colTypeFull, that.colTypeFull) && Objects.equals(colType, that.colType) && Objects.equals(colPrecision, that.colPrecision) && Objects.equals(colScale, that.colScale) && Objects.equals(colIdx, that.colIdx) && Objects.equals(tblComment, that.tblComment) && Objects.equals(colComment, that.colComment) && Objects.equals(cdId, that.cdId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tid, bupdate, hiveOwner, hiveTablename, colName, colTypeFull, colType, colPrecision, colScale, colIdx, tblComment, colComment, cdId);
    }
}
