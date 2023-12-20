package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_TBL_HDP实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpTblHdpDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * TID
     */
    private String tid;

    /**
     * HIVE_OWNER
     */
    private String hiveOwner;

    /**
     * HIVE_TABLENAME
     */
    private String hiveTablename;

    /**
     * COL_NAME
     */
    private String colName;

    /**
     * COL_TYPE_FULL
     */
    private String colTypeFull;

    /**
     * COL_TYPE
     */
    private String colType;

    /**
     * COL_PRECISION
     */
    private Long colPrecision;

    /**
     * COL_SCALE
     */
    private Long colScale;

    /**
     * COL_IDX
     */
    private Long colIdx;

    /**
     * TBL_COMMENT
     */
    private String tblComment;

    /**
     * COL_COMMENT
     */
    private String colComment;

    /**
     * UPDT_DATE
     */
    private Date updtDate;

    /**
     * CD_ID
     */
    private Long cdId;

    public String getTid() {
        return this.tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getHiveOwner() {
        return this.hiveOwner;
    }

    public void setHiveOwner(String hiveOwner) {
        this.hiveOwner = hiveOwner;
    }

    public String getHiveTablename() {
        return this.hiveTablename;
    }

    public void setHiveTablename(String hiveTablename) {
        this.hiveTablename = hiveTablename;
    }

    public String getColName() {
        return this.colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getColTypeFull() {
        return this.colTypeFull;
    }

    public void setColTypeFull(String colTypeFull) {
        this.colTypeFull = colTypeFull;
    }

    public String getColType() {
        return this.colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public Long getColPrecision() {
        return this.colPrecision;
    }

    public void setColPrecision(Long colPrecision) {
        this.colPrecision = colPrecision;
    }

    public Long getColScale() {
        return this.colScale;
    }

    public void setColScale(Long colScale) {
        this.colScale = colScale;
    }

    public Long getColIdx() {
        return this.colIdx;
    }

    public void setColIdx(Long colIdx) {
        this.colIdx = colIdx;
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

    public Date getUpdtDate() {
        return this.updtDate;
    }

    public void setUpdtDate(Date updtDate) {
        this.updtDate = updtDate;
    }

    public Long getCdId() {
        return this.cdId;
    }

    public void setCdId(Long cdId) {
        this.cdId = cdId;
    }

}