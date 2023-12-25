package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_DS2_MID", schema = "STG01", catalog = "")
public class VwImpDs2MidEntity {
    @Basic
    @Column(name = "DS_ID", nullable = false, length = 32)
    private String dsId;
    @Basic
    @Column(name = "DS_NAME", nullable = true, length = 339)
    private String dsName;
    @Basic
    @Column(name = "TASK_GROUP", nullable = false, length = 32)
    private String taskGroup;
    @Basic
    @Column(name = "SOU_ISHDP", nullable = true, length = 1)
    private String souIshdp;
    @Basic
    @Column(name = "SOU_ALLSQL", nullable = true, precision = 0)
    private BigInteger souAllsql;
    @Basic
    @Column(name = "SOU_TABLE", nullable = true, length = 4000)
    private String souTable;
    @Basic
    @Column(name = "SOU_FILTER", nullable = true, length = 1000)
    private String souFilter;
    @Basic
    @Column(name = "SOU_ISTAB", nullable = true, precision = 0)
    private BigInteger souIstab;
    @Basic
    @Column(name = "DEST_SYSID", nullable = false, length = 32)
    private String destSysid;
    @Basic
    @Column(name = "DEST_SYSNAME", nullable = true, length = 210)
    private String destSysname;
    @Basic
    @Column(name = "COL_MAP", nullable = true, length = 4000)
    private String colMap;
    @Basic
    @Column(name = "PARAM_SOU", nullable = true, length = 1)
    private String paramSou;
    @Basic
    @Column(name = "RETRY_CNT", nullable = true, precision = 0)
    private BigInteger retryCnt;
    @Basic
    @Column(name = "D_CONN", nullable = true, length = 500)
    private String dConn;
    @Basic
    @Column(name = "D_USER", nullable = true, length = 64)
    private String dUser;
    @Basic
    @Column(name = "D_PASS", nullable = true, length = 64)
    private String dPass;
    @Basic
    @Column(name = "D_CONN_FULL", nullable = true, length = 630)
    private String dConnFull;
    @Basic
    @Column(name = "DB_KIND", nullable = true, length = 4)
    private String dbKind;
    @Basic
    @Column(name = "DB_KIND_FULL", nullable = true, length = 2000)
    private String dbKindFull;
    @Basic
    @Column(name = "DEST_OWNER", nullable = true, length = 500)
    private String destOwner;
    @Basic
    @Column(name = "DEST_TABLENAME", nullable = true, length = 128)
    private String destTablename;
    @Basic
    @Column(name = "FLAG", nullable = true, length = 1)
    private String flag;
    @Basic
    @Column(name = "TBL_ID", nullable = true, length = 32)
    private String tblId;
    @Basic
    @Column(name = "PRE_SQL", nullable = true, length = 2000)
    private String preSql;
    @Basic
    @Column(name = "POST_SQL", nullable = true, length = 2000)
    private String postSql;
    @Basic
    @Column(name = "MAX_RUNTIME", nullable = true, precision = 0)
    private BigInteger maxRuntime;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "END_TIME", nullable = true)
    private Date endTime;
    @Basic
    @Column(name = "RUNTIME", nullable = true, precision = 0)
    private BigInteger runtime;
    @Basic
    @Column(name = "SOU_DB_CONN", nullable = true, length = 161)
    private String souDbConn;
    @Basic
    @Column(name = "DSVIEW", nullable = true, length = 33)
    private String dsview;
    @Basic
    @Column(name = "BVALID", nullable = true, precision = 0)
    private BigInteger bvalid;
    @Basic
    @Column(name = "START_TIME_REAL", nullable = true)
    private Date startTimeReal;
    @Basic
    @Column(name = "END_TIME_REAL", nullable = true)
    private Date endTimeReal;

    public String getDsId() {
        return dsId;
    }

    public void setDsId(String dsId) {
        this.dsId = dsId;
    }

    public String getDsName() {
        return dsName;
    }

    public void setDsName(String dsName) {
        this.dsName = dsName;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getSouIshdp() {
        return souIshdp;
    }

    public void setSouIshdp(String souIshdp) {
        this.souIshdp = souIshdp;
    }

    public BigInteger getSouAllsql() {
        return souAllsql;
    }

    public void setSouAllsql(BigInteger souAllsql) {
        this.souAllsql = souAllsql;
    }

    public String getSouTable() {
        return souTable;
    }

    public void setSouTable(String souTable) {
        this.souTable = souTable;
    }

    public String getSouFilter() {
        return souFilter;
    }

    public void setSouFilter(String souFilter) {
        this.souFilter = souFilter;
    }

    public BigInteger getSouIstab() {
        return souIstab;
    }

    public void setSouIstab(BigInteger souIstab) {
        this.souIstab = souIstab;
    }

    public String getDestSysid() {
        return destSysid;
    }

    public void setDestSysid(String destSysid) {
        this.destSysid = destSysid;
    }

    public String getDestSysname() {
        return destSysname;
    }

    public void setDestSysname(String destSysname) {
        this.destSysname = destSysname;
    }

    public String getColMap() {
        return colMap;
    }

    public void setColMap(String colMap) {
        this.colMap = colMap;
    }

    public String getParamSou() {
        return paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public BigInteger getRetryCnt() {
        return retryCnt;
    }

    public void setRetryCnt(BigInteger retryCnt) {
        this.retryCnt = retryCnt;
    }

    public String getdConn() {
        return dConn;
    }

    public void setdConn(String dConn) {
        this.dConn = dConn;
    }

    public String getdUser() {
        return dUser;
    }

    public void setdUser(String dUser) {
        this.dUser = dUser;
    }

    public String getdPass() {
        return dPass;
    }

    public void setdPass(String dPass) {
        this.dPass = dPass;
    }

    public String getdConnFull() {
        return dConnFull;
    }

    public void setdConnFull(String dConnFull) {
        this.dConnFull = dConnFull;
    }

    public String getDbKind() {
        return dbKind;
    }

    public void setDbKind(String dbKind) {
        this.dbKind = dbKind;
    }

    public String getDbKindFull() {
        return dbKindFull;
    }

    public void setDbKindFull(String dbKindFull) {
        this.dbKindFull = dbKindFull;
    }

    public String getDestOwner() {
        return destOwner;
    }

    public void setDestOwner(String destOwner) {
        this.destOwner = destOwner;
    }

    public String getDestTablename() {
        return destTablename;
    }

    public void setDestTablename(String destTablename) {
        this.destTablename = destTablename;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getTblId() {
        return tblId;
    }

    public void setTblId(String tblId) {
        this.tblId = tblId;
    }

    public String getPreSql() {
        return preSql;
    }

    public void setPreSql(String preSql) {
        this.preSql = preSql;
    }

    public String getPostSql() {
        return postSql;
    }

    public void setPostSql(String postSql) {
        this.postSql = postSql;
    }

    public BigInteger getMaxRuntime() {
        return maxRuntime;
    }

    public void setMaxRuntime(BigInteger maxRuntime) {
        this.maxRuntime = maxRuntime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public BigInteger getRuntime() {
        return runtime;
    }

    public void setRuntime(BigInteger runtime) {
        this.runtime = runtime;
    }

    public String getSouDbConn() {
        return souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getDsview() {
        return dsview;
    }

    public void setDsview(String dsview) {
        this.dsview = dsview;
    }

    public BigInteger getBvalid() {
        return bvalid;
    }

    public void setBvalid(BigInteger bvalid) {
        this.bvalid = bvalid;
    }

    public Date getStartTimeReal() {
        return startTimeReal;
    }

    public void setStartTimeReal(Date startTimeReal) {
        this.startTimeReal = startTimeReal;
    }

    public Date getEndTimeReal() {
        return endTimeReal;
    }

    public void setEndTimeReal(Date endTimeReal) {
        this.endTimeReal = endTimeReal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpDs2MidEntity that = (VwImpDs2MidEntity) o;
        return Objects.equals(dsId, that.dsId) && Objects.equals(dsName, that.dsName) && Objects.equals(taskGroup, that.taskGroup) && Objects.equals(souIshdp, that.souIshdp) && Objects.equals(souAllsql, that.souAllsql) && Objects.equals(souTable, that.souTable) && Objects.equals(souFilter, that.souFilter) && Objects.equals(souIstab, that.souIstab) && Objects.equals(destSysid, that.destSysid) && Objects.equals(destSysname, that.destSysname) && Objects.equals(colMap, that.colMap) && Objects.equals(paramSou, that.paramSou) && Objects.equals(retryCnt, that.retryCnt) && Objects.equals(dConn, that.dConn) && Objects.equals(dUser, that.dUser) && Objects.equals(dPass, that.dPass) && Objects.equals(dConnFull, that.dConnFull) && Objects.equals(dbKind, that.dbKind) && Objects.equals(dbKindFull, that.dbKindFull) && Objects.equals(destOwner, that.destOwner) && Objects.equals(destTablename, that.destTablename) && Objects.equals(flag, that.flag) && Objects.equals(tblId, that.tblId) && Objects.equals(preSql, that.preSql) && Objects.equals(postSql, that.postSql) && Objects.equals(maxRuntime, that.maxRuntime) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(runtime, that.runtime) && Objects.equals(souDbConn, that.souDbConn) && Objects.equals(dsview, that.dsview) && Objects.equals(bvalid, that.bvalid) && Objects.equals(startTimeReal, that.startTimeReal) && Objects.equals(endTimeReal, that.endTimeReal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dsId, dsName, taskGroup, souIshdp, souAllsql, souTable, souFilter, souIstab, destSysid, destSysname, colMap, paramSou, retryCnt, dConn, dUser, dPass, dConnFull, dbKind, dbKindFull, destOwner, destTablename, flag, tblId, preSql, postSql, maxRuntime, startTime, endTime, runtime, souDbConn, dsview, bvalid, startTimeReal, endTimeReal);
    }
}
