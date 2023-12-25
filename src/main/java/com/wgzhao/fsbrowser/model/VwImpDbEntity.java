package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_DB", schema = "STG01", catalog = "")
public class VwImpDbEntity {
    @Basic
    @Column(name = "DB_NAME", nullable = true, length = 200)
    private String dbName;
    @Basic
    @Column(name = "DB_CONSTR", nullable = true, length = 500)
    private String dbConstr;
    @Basic
    @Column(name = "DB_ID_ETL", nullable = true, length = 64)
    private String dbIdEtl;
    @Basic
    @Column(name = "DB_USER_ETL", nullable = true, length = 64)
    private String dbUserEtl;
    @Basic
    @Column(name = "DB_PASS_ETL", nullable = true, length = 64)
    private String dbPassEtl;
    @Basic
    @Column(name = "DB_PARAL_ETL", nullable = true, precision = 0)
    private BigInteger dbParalEtl;
    @Basic
    @Column(name = "DB_ID_DS", nullable = true, length = 64)
    private String dbIdDs;
    @Basic
    @Column(name = "DB_USER_DS", nullable = true, length = 64)
    private String dbUserDs;
    @Basic
    @Column(name = "DB_PASS_DS", nullable = true, length = 64)
    private String dbPassDs;
    @Basic
    @Column(name = "DB_PARAL_DS", nullable = true, precision = 0)
    private BigInteger dbParalDs;
    @Basic
    @Column(name = "DB_START", nullable = true, length = 32)
    private String dbStart;
    @Basic
    @Column(name = "DB_START_TYPE", nullable = true, length = 1)
    private String dbStartType;
    @Basic
    @Column(name = "DB_JUDGE_SQL", nullable = true, length = 4000)
    private String dbJudgeSql;
    @Basic
    @Column(name = "DB_JUDGE_PRE", nullable = true, length = 4000)
    private String dbJudgePre;
    @Basic
    @Column(name = "DB_REMARK", nullable = true)
    private String dbRemark;
    @Basic
    @Column(name = "DID", nullable = true, length = 32)
    private String did;
    @Basic
    @Column(name = "BVALID", nullable = true, length = 1)
    private String bvalid;
    @Basic
    @Column(name = "SYS_NAME", nullable = true, length = 210)
    private String sysName;
    @Basic
    @Column(name = "NETCHK", nullable = true, length = 2000)
    private String netchk;
    @Basic
    @Column(name = "DB_KIND", nullable = true, length = 4)
    private String dbKind;
    @Basic
    @Column(name = "DB_KIND_FULL", nullable = true, length = 2000)
    private String dbKindFull;
    @Basic
    @Column(name = "CONF", nullable = true, length = 4000)
    private String conf;

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

    public String getDbIdEtl() {
        return dbIdEtl;
    }

    public void setDbIdEtl(String dbIdEtl) {
        this.dbIdEtl = dbIdEtl;
    }

    public String getDbUserEtl() {
        return dbUserEtl;
    }

    public void setDbUserEtl(String dbUserEtl) {
        this.dbUserEtl = dbUserEtl;
    }

    public String getDbPassEtl() {
        return dbPassEtl;
    }

    public void setDbPassEtl(String dbPassEtl) {
        this.dbPassEtl = dbPassEtl;
    }

    public BigInteger getDbParalEtl() {
        return dbParalEtl;
    }

    public void setDbParalEtl(BigInteger dbParalEtl) {
        this.dbParalEtl = dbParalEtl;
    }

    public String getDbIdDs() {
        return dbIdDs;
    }

    public void setDbIdDs(String dbIdDs) {
        this.dbIdDs = dbIdDs;
    }

    public String getDbUserDs() {
        return dbUserDs;
    }

    public void setDbUserDs(String dbUserDs) {
        this.dbUserDs = dbUserDs;
    }

    public String getDbPassDs() {
        return dbPassDs;
    }

    public void setDbPassDs(String dbPassDs) {
        this.dbPassDs = dbPassDs;
    }

    public BigInteger getDbParalDs() {
        return dbParalDs;
    }

    public void setDbParalDs(BigInteger dbParalDs) {
        this.dbParalDs = dbParalDs;
    }

    public String getDbStart() {
        return dbStart;
    }

    public void setDbStart(String dbStart) {
        this.dbStart = dbStart;
    }

    public String getDbStartType() {
        return dbStartType;
    }

    public void setDbStartType(String dbStartType) {
        this.dbStartType = dbStartType;
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

    public String getDbRemark() {
        return dbRemark;
    }

    public void setDbRemark(String dbRemark) {
        this.dbRemark = dbRemark;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getBvalid() {
        return bvalid;
    }

    public void setBvalid(String bvalid) {
        this.bvalid = bvalid;
    }

    public String getSysName() {
        return sysName;
    }

    public void setSysName(String sysName) {
        this.sysName = sysName;
    }

    public String getNetchk() {
        return netchk;
    }

    public void setNetchk(String netchk) {
        this.netchk = netchk;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpDbEntity that = (VwImpDbEntity) o;
        return Objects.equals(dbName, that.dbName) && Objects.equals(dbConstr, that.dbConstr) && Objects.equals(dbIdEtl, that.dbIdEtl) && Objects.equals(dbUserEtl, that.dbUserEtl) && Objects.equals(dbPassEtl, that.dbPassEtl) && Objects.equals(dbParalEtl, that.dbParalEtl) && Objects.equals(dbIdDs, that.dbIdDs) && Objects.equals(dbUserDs, that.dbUserDs) && Objects.equals(dbPassDs, that.dbPassDs) && Objects.equals(dbParalDs, that.dbParalDs) && Objects.equals(dbStart, that.dbStart) && Objects.equals(dbStartType, that.dbStartType) && Objects.equals(dbJudgeSql, that.dbJudgeSql) && Objects.equals(dbJudgePre, that.dbJudgePre) && Objects.equals(dbRemark, that.dbRemark) && Objects.equals(did, that.did) && Objects.equals(bvalid, that.bvalid) && Objects.equals(sysName, that.sysName) && Objects.equals(netchk, that.netchk) && Objects.equals(dbKind, that.dbKind) && Objects.equals(dbKindFull, that.dbKindFull) && Objects.equals(conf, that.conf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbName, dbConstr, dbIdEtl, dbUserEtl, dbPassEtl, dbParalEtl, dbIdDs, dbUserDs, dbPassDs, dbParalDs, dbStart, dbStartType, dbJudgeSql, dbJudgePre, dbRemark, did, bvalid, sysName, netchk, dbKind, dbKindFull, conf);
    }
}
