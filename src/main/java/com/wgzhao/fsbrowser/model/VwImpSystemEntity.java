package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SYSTEM", schema = "STG01", catalog = "")
public class VwImpSystemEntity {
    @Basic
    @Column(name = "SYS_KIND", nullable = true, length = 3)
    private String sysKind;
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
    @Column(name = "DB_CONSTR", nullable = true, length = 500)
    private String dbConstr;
    @Basic
    @Column(name = "NETCHK", nullable = true, length = 2000)
    private String netchk;
    @Basic
    @Column(name = "BVALID", nullable = true, precision = 0)
    private BigInteger bvalid;
    @Basic
    @Column(name = "DB_USER", nullable = true, length = 64)
    private String dbUser;
    @Basic
    @Column(name = "DB_PASS", nullable = true, length = 64)
    private String dbPass;
    @Basic
    @Column(name = "DB_PARAL", nullable = true, precision = 0)
    private BigInteger dbParal;
    @Basic
    @Column(name = "DB_KIND", nullable = true, length = 4)
    private String dbKind;
    @Basic
    @Column(name = "DB_KIND_FULL", nullable = true, length = 2000)
    private String dbKindFull;
    @Basic
    @Column(name = "CONF", nullable = true, length = 4000)
    private String conf;
    @Basic
    @Column(name = "START_KIND", nullable = true, length = 6)
    private String startKind;
    @Basic
    @Column(name = "DB_START_TYPE", nullable = true, length = 1)
    private String dbStartType;
    @Basic
    @Column(name = "DB_START", nullable = true, length = 32)
    private String dbStart;
    @Basic
    @Column(name = "DB_START_DT", nullable = true, length = 2000)
    private String dbStartDt;
    @Basic
    @Column(name = "CREATE_DB", nullable = true, length = 182)
    private String createDb;
    @Basic
    @Column(name = "DB_JUDGE_SQL", nullable = true, length = 4000)
    private String dbJudgeSql;
    @Basic
    @Column(name = "DB_JUDGE_PRE", nullable = true, length = 4000)
    private String dbJudgePre;
    @Basic
    @Column(name = "DB_CONN", nullable = true, length = 645)
    private String dbConn;
    @Basic
    @Column(name = "DB_CONN_CMD", nullable = true, length = 4000)
    private String dbConnCmd;

    public String getSysKind() {
        return sysKind;
    }

    public void setSysKind(String sysKind) {
        this.sysKind = sysKind;
    }

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

    public String getDbConstr() {
        return dbConstr;
    }

    public void setDbConstr(String dbConstr) {
        this.dbConstr = dbConstr;
    }

    public String getNetchk() {
        return netchk;
    }

    public void setNetchk(String netchk) {
        this.netchk = netchk;
    }

    public BigInteger getBvalid() {
        return bvalid;
    }

    public void setBvalid(BigInteger bvalid) {
        this.bvalid = bvalid;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPass() {
        return dbPass;
    }

    public void setDbPass(String dbPass) {
        this.dbPass = dbPass;
    }

    public BigInteger getDbParal() {
        return dbParal;
    }

    public void setDbParal(BigInteger dbParal) {
        this.dbParal = dbParal;
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

    public String getConf() {
        return conf;
    }

    public void setConf(String conf) {
        this.conf = conf;
    }

    public String getStartKind() {
        return startKind;
    }

    public void setStartKind(String startKind) {
        this.startKind = startKind;
    }

    public String getDbStartType() {
        return dbStartType;
    }

    public void setDbStartType(String dbStartType) {
        this.dbStartType = dbStartType;
    }

    public String getDbStart() {
        return dbStart;
    }

    public void setDbStart(String dbStart) {
        this.dbStart = dbStart;
    }

    public String getDbStartDt() {
        return dbStartDt;
    }

    public void setDbStartDt(String dbStartDt) {
        this.dbStartDt = dbStartDt;
    }

    public String getCreateDb() {
        return createDb;
    }

    public void setCreateDb(String createDb) {
        this.createDb = createDb;
    }

    public String getDbJudgeSql() {
        return dbJudgeSql;
    }

    public void setDbJudgeSql(String dbJudgeSql) {
        this.dbJudgeSql = dbJudgeSql;
    }

    public String getDbJudgePre() {
        return dbJudgePre;
    }

    public void setDbJudgePre(String dbJudgePre) {
        this.dbJudgePre = dbJudgePre;
    }

    public String getDbConn() {
        return dbConn;
    }

    public void setDbConn(String dbConn) {
        this.dbConn = dbConn;
    }

    public String getDbConnCmd() {
        return dbConnCmd;
    }

    public void setDbConnCmd(String dbConnCmd) {
        this.dbConnCmd = dbConnCmd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpSystemEntity that = (VwImpSystemEntity) o;
        return Objects.equals(sysKind, that.sysKind) && Objects.equals(sysid, that.sysid) && Objects.equals(sysName, that.sysName) && Objects.equals(dbName, that.dbName) && Objects.equals(dbConstr, that.dbConstr) && Objects.equals(netchk, that.netchk) && Objects.equals(bvalid, that.bvalid) && Objects.equals(dbUser, that.dbUser) && Objects.equals(dbPass, that.dbPass) && Objects.equals(dbParal, that.dbParal) && Objects.equals(dbKind, that.dbKind) && Objects.equals(dbKindFull, that.dbKindFull) && Objects.equals(conf, that.conf) && Objects.equals(startKind, that.startKind) && Objects.equals(dbStartType, that.dbStartType) && Objects.equals(dbStart, that.dbStart) && Objects.equals(dbStartDt, that.dbStartDt) && Objects.equals(createDb, that.createDb) && Objects.equals(dbJudgeSql, that.dbJudgeSql) && Objects.equals(dbJudgePre, that.dbJudgePre) && Objects.equals(dbConn, that.dbConn) && Objects.equals(dbConnCmd, that.dbConnCmd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sysKind, sysid, sysName, dbName, dbConstr, netchk, bvalid, dbUser, dbPass, dbParal, dbKind, dbKindFull, conf, startKind, dbStartType, dbStart, dbStartDt, createDb, dbJudgeSql, dbJudgePre, dbConn, dbConnCmd);
    }
}
