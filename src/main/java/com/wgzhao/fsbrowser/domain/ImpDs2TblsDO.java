package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_DS2_TBLS实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpDs2TblsDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * DS_ID
     */
    private String dsId;

    /**
     * 是否从hdp推送
     */
    private String souIshdp;

    /**
     * SOU_TABLE
     */
    private String souTable;

    /**
     * 源筛选条件
     */
    private String souFilter;

    /**
     * DEST_OWNER
     */
    private String destOwner;

    /**
     * DEST_TABLENAME
     */
    private String destTablename;

    /**
     * FLAG
     */
    private String flag;

    /**
     * START_TIME
     */
    private Date startTime;

    /**
     * END_TIME
     */
    private Date endTime;

    /**
     * TBL_ID
     */
    private String tblId;

    /**
     * PRE_SQL
     */
    private String preSql;

    /**
     * COL_MAP
     */
    private String colMap;

    /**
     * POST_SQL
     */
    private String postSql;

    /**
     * 允许最长耗时
     */
    private Long maxRuntime;

    public String getDsId() {
        return this.dsId;
    }

    public void setDsId(String dsId) {
        this.dsId = dsId;
    }

    public String getSouIshdp() {
        return this.souIshdp;
    }

    public void setSouIshdp(String souIshdp) {
        this.souIshdp = souIshdp;
    }

    public String getSouTable() {
        return this.souTable;
    }

    public void setSouTable(String souTable) {
        this.souTable = souTable;
    }

    public String getSouFilter() {
        return this.souFilter;
    }

    public void setSouFilter(String souFilter) {
        this.souFilter = souFilter;
    }

    public String getDestOwner() {
        return this.destOwner;
    }

    public void setDestOwner(String destOwner) {
        this.destOwner = destOwner;
    }

    public String getDestTablename() {
        return this.destTablename;
    }

    public void setDestTablename(String destTablename) {
        this.destTablename = destTablename;
    }

    public String getFlag() {
        return this.flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return this.endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getTblId() {
        return this.tblId;
    }

    public void setTblId(String tblId) {
        this.tblId = tblId;
    }

    public String getPreSql() {
        return this.preSql;
    }

    public void setPreSql(String preSql) {
        this.preSql = preSql;
    }

    public String getColMap() {
        return this.colMap;
    }

    public void setColMap(String colMap) {
        this.colMap = colMap;
    }

    public String getPostSql() {
        return this.postSql;
    }

    public void setPostSql(String postSql) {
        this.postSql = postSql;
    }

    public Long getMaxRuntime() {
        return this.maxRuntime;
    }

    public void setMaxRuntime(Long maxRuntime) {
        this.maxRuntime = maxRuntime;
    }

}