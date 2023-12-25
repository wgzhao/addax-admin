package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL_COLS", schema = "STG01", catalog = "")
public class VwImpEtlColsEntity {
    @Basic
    @Column(name = "TID", nullable = false, length = 32)
    private String tid;
    @Basic
    @Column(name = "JOBKIND", nullable = true, length = 6)
    private String jobkind;
    @Basic
    @Column(name = "DATA_TYPE", nullable = true, length = 64)
    private String dataType;
    @Basic
    @Column(name = "COLUMN_NAME", nullable = true, length = 64)
    private String columnName;
    @Basic
    @Column(name = "BQUOTA", nullable = true, precision = 0)
    private BigInteger bquota;
    @Basic
    @Column(name = "COL_NAME", nullable = true, length = 255)
    private String colName;
    @Basic
    @Column(name = "COL_TYPE", nullable = true, length = 2000)
    private String colType;
    @Basic
    @Column(name = "COL_IDX", nullable = true, precision = 0)
    private BigInteger colIdx;
    @Basic
    @Column(name = "DBID", nullable = true, length = 64)
    private String dbid;

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getJobkind() {
        return jobkind;
    }

    public void setJobkind(String jobkind) {
        this.jobkind = jobkind;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public BigInteger getBquota() {
        return bquota;
    }

    public void setBquota(BigInteger bquota) {
        this.bquota = bquota;
    }

    public String getColName() {
        return colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getColType() {
        return colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public BigInteger getColIdx() {
        return colIdx;
    }

    public void setColIdx(BigInteger colIdx) {
        this.colIdx = colIdx;
    }

    public String getDbid() {
        return dbid;
    }

    public void setDbid(String dbid) {
        this.dbid = dbid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpEtlColsEntity that = (VwImpEtlColsEntity) o;
        return Objects.equals(tid, that.tid) && Objects.equals(jobkind, that.jobkind) && Objects.equals(dataType, that.dataType) && Objects.equals(columnName, that.columnName) && Objects.equals(bquota, that.bquota) && Objects.equals(colName, that.colName) && Objects.equals(colType, that.colType) && Objects.equals(colIdx, that.colIdx) && Objects.equals(dbid, that.dbid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tid, jobkind, dataType, columnName, bquota, colName, colType, colIdx, dbid);
    }
}
