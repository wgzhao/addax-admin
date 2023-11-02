package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="tb_imp_db")
public class ImpDB {

    @Id
    @Column(name = "did")
    private String id;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "db_constr")
    private String dbConstr;

    @Column(name = "db_id_etl")
    private String dbIdEtl;

    @Column(name = "db_user_etl")
    private String dbUserEtl;

    @Column(name = "db_pass_etl")
    private String dbPassEtl;

    @Column(name = "db_paral_etl")
    private Integer dbParalEtl;

    @Column(name = "db_id_ds")
    private String DbIdDs;

    @Column(name = "db_user_ds")
    private String dbUserDs;

    @Column(name = "db_pass_ds")
    private String dbPassDs;

    @Column(name = "db_paral_ds")
    private Integer dbParalDs;

    @Column(name = "db_start")
    private String dbStart;

    @Column(name = "db_start_type")
    private String dbStartType;

    @Column(name = "db_judge_sql")
    private String dbJudgeSql;

    @Column(name = "db_judge_pre")
    private String  dbJudgePre;

    @Column(name = "db_remark")
    private String dbRemark;

    @Column(name = "bvalid")
    private String bvalid;

    @Column(name = "conf")
    private String conf;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getDbParalEtl() {
        return dbParalEtl;
    }

    public void setDbParalEtl(Integer dbParalEtl) {
        this.dbParalEtl = dbParalEtl;
    }

    public String getDbIdDs() {
        return DbIdDs;
    }

    public void setDbIdDs(String dbIdDs) {
        DbIdDs = dbIdDs;
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

    public Integer getDbParalDs() {
        return dbParalDs;
    }

    public void setDbParalDs(Integer dbParalDs) {
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

    public String getBvalid() {
        return bvalid;
    }

    public void setBvalid(String bvalid) {
        this.bvalid = bvalid;
    }

    public String getConf() {
        return conf;
    }

    public void setConf(String conf) {
        this.conf = conf;
    }
}
