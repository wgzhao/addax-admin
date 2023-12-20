package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_TBL_SOU实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpTblSouDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * TID
     */
    private String tid;

    /**
     * SOU_DB_CONN
     */
    private String souDbConn;

    /**
     * SOU_OWNER
     */
    private String souOwner;

    /**
     * SOU_TABLENAME
     */
    private String souTablename;

    /**
     * COLUMN_NAME_ORIG
     */
    private String columnNameOrig;

    /**
     * COLUMN_NAME
     */
    private String columnName;

    /**
     * COLUMN_ID
     */
    private Long columnId;

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
     * TBL_COMMENT
     */
    private String tblComment;

    /**
     * COL_COMMENT
     */
    private String colComment;

    /**
     * DEST_TYPE
     */
    private String destType;

    /**
     * DEST_TYPE_FULL
     */
    private String destTypeFull;

    /**
     * UPDT_DATE
     */
    private Date updtDate;

    public String getTid() {
        return this.tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getSouDbConn() {
        return this.souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getSouOwner() {
        return this.souOwner;
    }

    public void setSouOwner(String souOwner) {
        this.souOwner = souOwner;
    }

    public String getSouTablename() {
        return this.souTablename;
    }

    public void setSouTablename(String souTablename) {
        this.souTablename = souTablename;
    }

    public String getColumnNameOrig() {
        return this.columnNameOrig;
    }

    public void setColumnNameOrig(String columnNameOrig) {
        this.columnNameOrig = columnNameOrig;
    }

    public String getColumnName() {
        return this.columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Long getColumnId() {
        return this.columnId;
    }

    public void setColumnId(Long columnId) {
        this.columnId = columnId;
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

    public String getTblComment() {
        return this.tblComment;
    }

    public void setTblComment(String tblComment) {
        this.tblComment = tblComment;
    }

    public String getColComment() {
        return this.colComment;
    }

    public void setColComment(String colComment) {
        this.colComment = colComment;
    }

    public String getDestType() {
        return this.destType;
    }

    public void setDestType(String destType) {
        this.destType = destType;
    }

    public String getDestTypeFull() {
        return this.destTypeFull;
    }

    public void setDestTypeFull(String destTypeFull) {
        this.destTypeFull = destTypeFull;
    }

    public Date getUpdtDate() {
        return this.updtDate;
    }

    public void setUpdtDate(Date updtDate) {
        this.updtDate = updtDate;
    }

}