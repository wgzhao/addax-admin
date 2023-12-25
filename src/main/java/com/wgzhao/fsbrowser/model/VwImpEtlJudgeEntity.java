package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL_JUDGE", schema = "STG01", catalog = "")
public class VwImpEtlJudgeEntity {
    @Basic
    @Column(name = "SYSID", nullable = true, length = 64)
    private String sysid;
    @Basic
    @Column(name = "SYS_NAME", nullable = true, length = 210)
    private String sysName;
    @Basic
    @Column(name = "DB_NAME", nullable = true, length = 67)
    private String dbName;
    @Basic
    @Column(name = "JUDGE_SQL", nullable = true, length = 4000)
    private String judgeSql;
    @Basic
    @Column(name = "JUDGE_PRE", nullable = true, length = 4000)
    private String judgePre;
    @Basic
    @Column(name = "BSTART", nullable = true, length = 32)
    private String bstart;
    @Basic
    @Column(name = "FVAL", nullable = true, length = 32)
    private String fval;
    @Basic
    @Column(name = "JUDGE_TIME", nullable = true)
    private Date judgeTime;
    @Basic
    @Column(name = "PX", nullable = true, precision = 0)
    private BigInteger px;
    @Basic
    @Column(name = "PARAM_VALUE", nullable = true, length = 4000)
    private String paramValue;
    @Basic
    @Column(name = "DB_CONN", nullable = true, length = 645)
    private String dbConn;

    public String getSysid() {
        return sysid;
    }

    public void setSysid(String sysid) {
        this.sysid = sysid;
    }

    public String getSysName() {
        return sysName;
    }

    public void setSysName(String sysName) {
        this.sysName = sysName;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getJudgeSql() {
        return judgeSql;
    }

    public void setJudgeSql(String judgeSql) {
        this.judgeSql = judgeSql;
    }

    public String getJudgePre() {
        return judgePre;
    }

    public void setJudgePre(String judgePre) {
        this.judgePre = judgePre;
    }

    public String getBstart() {
        return bstart;
    }

    public void setBstart(String bstart) {
        this.bstart = bstart;
    }

    public String getFval() {
        return fval;
    }

    public void setFval(String fval) {
        this.fval = fval;
    }

    public Date getJudgeTime() {
        return judgeTime;
    }

    public void setJudgeTime(Date judgeTime) {
        this.judgeTime = judgeTime;
    }

    public BigInteger getPx() {
        return px;
    }

    public void setPx(BigInteger px) {
        this.px = px;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public String getDbConn() {
        return dbConn;
    }

    public void setDbConn(String dbConn) {
        this.dbConn = dbConn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpEtlJudgeEntity that = (VwImpEtlJudgeEntity) o;
        return Objects.equals(sysid, that.sysid) && Objects.equals(sysName, that.sysName) && Objects.equals(dbName, that.dbName) && Objects.equals(judgeSql, that.judgeSql) && Objects.equals(judgePre, that.judgePre) && Objects.equals(bstart, that.bstart) && Objects.equals(fval, that.fval) && Objects.equals(judgeTime, that.judgeTime) && Objects.equals(px, that.px) && Objects.equals(paramValue, that.paramValue) && Objects.equals(dbConn, that.dbConn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sysid, sysName, dbName, judgeSql, judgePre, bstart, fval, judgeTime, px, paramValue, dbConn);
    }
}
