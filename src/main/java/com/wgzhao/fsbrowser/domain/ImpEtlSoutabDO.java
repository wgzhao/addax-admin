package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_ETL_SOUTAB实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpEtlSoutabDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SOU_DB_CONN
     */
    private String souDbConn;

    /**
     * OWNER
     */
    private String owner;

    /**
     * TABLE_NAME
     */
    private String tableName;

    /**
     * COLUMN_NAME
     */
    private String columnName;

    /**
     * DATA_TYPE
     */
    private String dataType;

    /**
     * DATA_LENGTH
     */
    private Long dataLength;

    /**
     * DATA_PRECISION
     */
    private Long dataPrecision;

    /**
     * DATA_SCALE
     */
    private Long dataScale;

    /**
     * COLUMN_ID
     */
    private Long columnId;

    /**
     * TABLE_TYPE
     */
    private String tableType;

    /**
     * TAB_COMMENT
     */
    private String tabComment;

    /**
     * COL_COMMENT
     */
    private String colComment;

    /**
     * DW_CLT_DATE
     */
    private Date dwCltDate;

    public String getSouDbConn() {
        return this.souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getOwner() {
        return this.owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getTableName() {
        return this.tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return this.columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return this.dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Long getDataLength() {
        return this.dataLength;
    }

    public void setDataLength(Long dataLength) {
        this.dataLength = dataLength;
    }

    public Long getDataPrecision() {
        return this.dataPrecision;
    }

    public void setDataPrecision(Long dataPrecision) {
        this.dataPrecision = dataPrecision;
    }

    public Long getDataScale() {
        return this.dataScale;
    }

    public void setDataScale(Long dataScale) {
        this.dataScale = dataScale;
    }

    public Long getColumnId() {
        return this.columnId;
    }

    public void setColumnId(Long columnId) {
        this.columnId = columnId;
    }

    public String getTableType() {
        return this.tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public String getTabComment() {
        return this.tabComment;
    }

    public void setTabComment(String tabComment) {
        this.tabComment = tabComment;
    }

    public String getColComment() {
        return this.colComment;
    }

    public void setColComment(String colComment) {
        this.colComment = colComment;
    }

    public Date getDwCltDate() {
        return this.dwCltDate;
    }

    public void setDwCltDate(Date dwCltDate) {
        this.dwCltDate = dwCltDate;
    }

}