package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_CHECK_SOUTAB", schema = "STG01", catalog = "")
public class VwImpCheckSoutabEntity {
    @Basic
    @Column(name = "SOU_DB_CONN", nullable = true, length = 64)
    private String souDbConn;
    @Basic
    @Column(name = "OWNER", nullable = true, length = 64)
    private String owner;
    @Basic
    @Column(name = "TABLE_NAME", nullable = true, length = 64)
    private String tableName;
    @Basic
    @Column(name = "COLUMN_NAME", nullable = true, length = 64)
    private String columnName;
    @Basic
    @Column(name = "DATA_TYPE", nullable = true, length = 64)
    private String dataType;
    @Basic
    @Column(name = "DATA_TYPE_LAST", nullable = true, length = 64)
    private String dataTypeLast;
    @Basic
    @Column(name = "HIVE_TYPE", nullable = true, length = 2000)
    private String hiveType;
    @Basic
    @Column(name = "HIVE_TYPE_LAST", nullable = true, length = 2000)
    private String hiveTypeLast;
    @Basic
    @Column(name = "DATA_LENGTH", nullable = true, precision = 0)
    private Integer dataLength;
    @Basic
    @Column(name = "DATA_LENGTH_LAST", nullable = true, precision = 0)
    private Integer dataLengthLast;
    @Basic
    @Column(name = "DATA_PRECISION", nullable = true, precision = 0)
    private Integer dataPrecision;
    @Basic
    @Column(name = "DATA_PRECISION_LAST", nullable = true, precision = 0)
    private Integer dataPrecisionLast;
    @Basic
    @Column(name = "DATA_SCALE", nullable = true, precision = 0)
    private Integer dataScale;
    @Basic
    @Column(name = "DATA_SCALE_LAST", nullable = true, precision = 0)
    private Integer dataScaleLast;
    @Basic
    @Column(name = "DW_CLT_DATE", nullable = true)
    private Date dwCltDate;
    @Basic
    @Column(name = "DW_CLT_DATE_LAST", nullable = true)
    private Date dwCltDateLast;

    public String getSouDbConn() {
        return souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataTypeLast() {
        return dataTypeLast;
    }

    public void setDataTypeLast(String dataTypeLast) {
        this.dataTypeLast = dataTypeLast;
    }

    public String getHiveType() {
        return hiveType;
    }

    public void setHiveType(String hiveType) {
        this.hiveType = hiveType;
    }

    public String getHiveTypeLast() {
        return hiveTypeLast;
    }

    public void setHiveTypeLast(String hiveTypeLast) {
        this.hiveTypeLast = hiveTypeLast;
    }

    public Integer getDataLength() {
        return dataLength;
    }

    public void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    public Integer getDataLengthLast() {
        return dataLengthLast;
    }

    public void setDataLengthLast(Integer dataLengthLast) {
        this.dataLengthLast = dataLengthLast;
    }

    public Integer getDataPrecision() {
        return dataPrecision;
    }

    public void setDataPrecision(Integer dataPrecision) {
        this.dataPrecision = dataPrecision;
    }

    public Integer getDataPrecisionLast() {
        return dataPrecisionLast;
    }

    public void setDataPrecisionLast(Integer dataPrecisionLast) {
        this.dataPrecisionLast = dataPrecisionLast;
    }

    public Integer getDataScale() {
        return dataScale;
    }

    public void setDataScale(Integer dataScale) {
        this.dataScale = dataScale;
    }

    public Integer getDataScaleLast() {
        return dataScaleLast;
    }

    public void setDataScaleLast(Integer dataScaleLast) {
        this.dataScaleLast = dataScaleLast;
    }

    public Date getDwCltDate() {
        return dwCltDate;
    }

    public void setDwCltDate(Date dwCltDate) {
        this.dwCltDate = dwCltDate;
    }

    public Date getDwCltDateLast() {
        return dwCltDateLast;
    }

    public void setDwCltDateLast(Date dwCltDateLast) {
        this.dwCltDateLast = dwCltDateLast;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpCheckSoutabEntity that = (VwImpCheckSoutabEntity) o;
        return Objects.equals(souDbConn, that.souDbConn) && Objects.equals(owner, that.owner) && Objects.equals(tableName, that.tableName) && Objects.equals(columnName, that.columnName) && Objects.equals(dataType, that.dataType) && Objects.equals(dataTypeLast, that.dataTypeLast) && Objects.equals(hiveType, that.hiveType) && Objects.equals(hiveTypeLast, that.hiveTypeLast) && Objects.equals(dataLength, that.dataLength) && Objects.equals(dataLengthLast, that.dataLengthLast) && Objects.equals(dataPrecision, that.dataPrecision) && Objects.equals(dataPrecisionLast, that.dataPrecisionLast) && Objects.equals(dataScale, that.dataScale) && Objects.equals(dataScaleLast, that.dataScaleLast) && Objects.equals(dwCltDate, that.dwCltDate) && Objects.equals(dwCltDateLast, that.dwCltDateLast);
    }

    @Override
    public int hashCode() {
        return Objects.hash(souDbConn, owner, tableName, columnName, dataType, dataTypeLast, hiveType, hiveTypeLast, dataLength, dataLengthLast, dataPrecision, dataPrecisionLast, dataScale, dataScaleLast, dwCltDate, dwCltDateLast);
    }
}
