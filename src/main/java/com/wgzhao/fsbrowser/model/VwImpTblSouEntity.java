package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TBL_SOU", schema = "STG01", catalog = "")
public class VwImpTblSouEntity {
    @Basic
    @Column(name = "TID", nullable = false, length = 32)
    private String tid;
    @Basic
    @Column(name = "BUPDATE", nullable = true, length = 1)
    private String bupdate;
    @Basic
    @Column(name = "SOU_DB_CONN", nullable = true, length = 35)
    private String souDbConn;
    @Basic
    @Column(name = "SOU_OWNER", nullable = true, length = 32)
    private String souOwner;
    @Basic
    @Column(name = "SOU_TABLENAME", nullable = true, length = 4000)
    private String souTablename;
    @Basic
    @Column(name = "COLUMN_NAME_ORIG", nullable = false, length = 64)
    private String columnNameOrig;
    @Basic
    @Column(name = "COLUMN_NAME", nullable = true, length = 71)
    private String columnName;
    @Basic
    @Column(name = "COLUMN_ID", nullable = true, precision = 0)
    private Integer columnId;
    @Basic
    @Column(name = "DATA_TYPE", nullable = true, length = 256)
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
    @Column(name = "TBL_COMMENT", nullable = true, length = 4000)
    private String tblComment;
    @Basic
    @Column(name = "COL_COMMENT", nullable = true, length = 4000)
    private String colComment;
    @Basic
    @Column(name = "DEST_TYPE", nullable = true, length = 2000)
    private String destType;
    @Basic
    @Column(name = "DEST_TYPE_FULL", nullable = true, length = 2083)
    private String destTypeFull;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpTblSouEntity that = (VwImpTblSouEntity) o;
        return Objects.equals(tid, that.tid) && Objects.equals(bupdate, that.bupdate) && Objects.equals(souDbConn, that.souDbConn) && Objects.equals(souOwner, that.souOwner) && Objects.equals(souTablename, that.souTablename) && Objects.equals(columnNameOrig, that.columnNameOrig) && Objects.equals(columnName, that.columnName) && Objects.equals(columnId, that.columnId) && Objects.equals(dataType, that.dataType) && Objects.equals(dataLength, that.dataLength) && Objects.equals(dataPrecision, that.dataPrecision) && Objects.equals(dataScale, that.dataScale) && Objects.equals(tblComment, that.tblComment) && Objects.equals(colComment, that.colComment) && Objects.equals(destType, that.destType) && Objects.equals(destTypeFull, that.destTypeFull);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tid, bupdate, souDbConn, souOwner, souTablename, columnNameOrig, columnName, columnId, dataType, dataLength, dataPrecision, dataScale, tblComment, colComment, destType, destTypeFull);
    }
}
