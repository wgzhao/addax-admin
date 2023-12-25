package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TBL", schema = "STG01", catalog = "")
public class VwImpTblEntity {
    @Basic
    @Column(name = "TID", nullable = true, length = 32)
    private String tid;
    @Basic
    @Column(name = "SOU_DB_CONN", nullable = true, length = 35)
    private String souDbConn;
    @Basic
    @Column(name = "SOU_OWNER", nullable = true, length = 32)
    private String souOwner;
    @Basic
    @Column(name = "SOU_TABLENAME", nullable = true, length = 64)
    private String souTablename;
    @Basic
    @Column(name = "COLUMN_NAME_ORIG", nullable = true, length = 64)
    private String columnNameOrig;
    @Basic
    @Column(name = "COLUMN_NAME", nullable = true, length = 71)
    private String columnName;
    @Basic
    @Column(name = "COLUMN_ID", nullable = true, precision = 0)
    private Integer columnId;
    @Basic
    @Column(name = "DATA_TYPE", nullable = true, length = 64)
    private String dataType;
    @Basic
    @Column(name = "DATA_LENGTH", nullable = true, precision = 0)
    private Integer dataLength;
    @Basic
    @Column(name = "DATA_PRECISION", nullable = true, precision = 0)
    private BigInteger dataPrecision;
    @Basic
    @Column(name = "DATA_SCALE", nullable = true, precision = 0)
    private BigInteger dataScale;
    @Basic
    @Column(name = "TABLE_COMMENT", nullable = true, length = 4000)
    private String tableComment;
    @Basic
    @Column(name = "COLUMN_COMMENT", nullable = true, length = 4000)
    private String columnComment;
    @Basic
    @Column(name = "DEST_TYPE", nullable = true, length = 2000)
    private String destType;
    @Basic
    @Column(name = "DEST_TYPE_FULL", nullable = true, length = 2083)
    private String destTypeFull;
    @Basic
    @Column(name = "HIVE_OWNER", nullable = true, length = 35)
    private String hiveOwner;
    @Basic
    @Column(name = "HIVE_TABLENAME", nullable = true, length = 64)
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

    public String getSouDbConn() {
        return souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getSouOwner() {
        return souOwner;
    }

    public void setSouOwner(String souOwner) {
        this.souOwner = souOwner;
    }

    public String getSouTablename() {
        return souTablename;
    }

    public void setSouTablename(String souTablename) {
        this.souTablename = souTablename;
    }

    public String getColumnNameOrig() {
        return columnNameOrig;
    }

    public void setColumnNameOrig(String columnNameOrig) {
        this.columnNameOrig = columnNameOrig;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Integer getColumnId() {
        return columnId;
    }

    public void setColumnId(Integer columnId) {
        this.columnId = columnId;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Integer getDataLength() {
        return dataLength;
    }

    public void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    public BigInteger getDataPrecision() {
        return dataPrecision;
    }

    public void setDataPrecision(BigInteger dataPrecision) {
        this.dataPrecision = dataPrecision;
    }

    public BigInteger getDataScale() {
        return dataScale;
    }

    public void setDataScale(BigInteger dataScale) {
        this.dataScale = dataScale;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public String getDestType() {
        return destType;
    }

    public void setDestType(String destType) {
        this.destType = destType;
    }

    public String getDestTypeFull() {
        return destTypeFull;
    }

    public void setDestTypeFull(String destTypeFull) {
        this.destTypeFull = destTypeFull;
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
        VwImpTblEntity that = (VwImpTblEntity) o;
        return Objects.equals(tid, that.tid) && Objects.equals(souDbConn, that.souDbConn) && Objects.equals(souOwner, that.souOwner) && Objects.equals(souTablename, that.souTablename) && Objects.equals(columnNameOrig, that.columnNameOrig) && Objects.equals(columnName, that.columnName) && Objects.equals(columnId, that.columnId) && Objects.equals(dataType, that.dataType) && Objects.equals(dataLength, that.dataLength) && Objects.equals(dataPrecision, that.dataPrecision) && Objects.equals(dataScale, that.dataScale) && Objects.equals(tableComment, that.tableComment) && Objects.equals(columnComment, that.columnComment) && Objects.equals(destType, that.destType) && Objects.equals(destTypeFull, that.destTypeFull) && Objects.equals(hiveOwner, that.hiveOwner) && Objects.equals(hiveTablename, that.hiveTablename) && Objects.equals(colName, that.colName) && Objects.equals(colTypeFull, that.colTypeFull) && Objects.equals(colType, that.colType) && Objects.equals(colPrecision, that.colPrecision) && Objects.equals(colScale, that.colScale) && Objects.equals(colIdx, that.colIdx) && Objects.equals(tblComment, that.tblComment) && Objects.equals(colComment, that.colComment) && Objects.equals(cdId, that.cdId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tid, souDbConn, souOwner, souTablename, columnNameOrig, columnName, columnId, dataType, dataLength, dataPrecision, dataScale, tableComment, columnComment, destType, destTypeFull, hiveOwner, hiveTablename, colName, colTypeFull, colType, colPrecision, colScale, colIdx, tblComment, colComment, cdId);
    }
}
