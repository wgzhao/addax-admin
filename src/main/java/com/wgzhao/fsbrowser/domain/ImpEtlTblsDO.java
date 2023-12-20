package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;

/**
 * HIVE的表结构信息实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpEtlTblsDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * DB_ID
     */
    private Long dbId;

    /**
     * 数据库名、用户
     */
    private String dbName;

    /**
     * 数据库所在HDFS地址
     */
    private String dbLocation;

    /**
     * TBL_ID
     */
    private Long tblId;

    /**
     * 表名
     */
    private String tblName;

    /**
     * 表类型（管理表、外部表）
     */
    private String tblType;

    /**
     * 表所在HDFS地址
     */
    private String tblLocation;

    /**
     * CD_ID
     */
    private Long cdId;

    /**
     * 字段名
     */
    private String colName;

    /**
     * 字段类型
     */
    private String colType;

    /**
     * 字段备注
     */
    private String colComment;

    /**
     * 字段顺序
     */
    private Long colIdx;

    /**
     * 表注释
     */
    private String tblComment;

    public Long getDbId() {
        return this.dbId;
    }

    public void setDbId(Long dbId) {
        this.dbId = dbId;
    }

    public String getDbName() {
        return this.dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbLocation() {
        return this.dbLocation;
    }

    public void setDbLocation(String dbLocation) {
        this.dbLocation = dbLocation;
    }

    public Long getTblId() {
        return this.tblId;
    }

    public void setTblId(Long tblId) {
        this.tblId = tblId;
    }

    public String getTblName() {
        return this.tblName;
    }

    public void setTblName(String tblName) {
        this.tblName = tblName;
    }

    public String getTblType() {
        return this.tblType;
    }

    public void setTblType(String tblType) {
        this.tblType = tblType;
    }

    public String getTblLocation() {
        return this.tblLocation;
    }

    public void setTblLocation(String tblLocation) {
        this.tblLocation = tblLocation;
    }

    public Long getCdId() {
        return this.cdId;
    }

    public void setCdId(Long cdId) {
        this.cdId = cdId;
    }

    public String getColName() {
        return this.colName;
    }

    public void setColName(String colName) {
        this.colName = colName;
    }

    public String getColType() {
        return this.colType;
    }

    public void setColType(String colType) {
        this.colType = colType;
    }

    public String getColComment() {
        return this.colComment;
    }

    public void setColComment(String colComment) {
        this.colComment = colComment;
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

}