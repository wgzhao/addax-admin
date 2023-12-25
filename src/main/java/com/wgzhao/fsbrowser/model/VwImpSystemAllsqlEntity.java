package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SYSTEM_ALLSQL", schema = "STG01", catalog = "")
public class VwImpSystemAllsqlEntity {
    @Basic
    @Column(name = "DB_KIND_FULL", nullable = true, length = 2000)
    private String dbKindFull;
    @Basic
    @Column(name = "DB_CONSTR", nullable = true, length = 500)
    private String dbConstr;
    @Basic
    @Column(name = "DB_USER", nullable = true, length = 64)
    private String dbUser;
    @Basic
    @Column(name = "DB_PASS", nullable = true, length = 64)
    private String dbPass;
    @Basic
    @Column(name = "SYSID", nullable = true, length = 64)
    private String sysid;

    public String getDbKindFull() {
        return dbKindFull;
    }

    public void setDbKindFull(String dbKindFull) {
        this.dbKindFull = dbKindFull;
    }

    public String getDbConstr() {
        return dbConstr;
    }

    public void setDbConstr(String dbConstr) {
        this.dbConstr = dbConstr;
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

    public String getSysid() {
        return sysid;
    }

    public void setSysid(String sysid) {
        this.sysid = sysid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpSystemAllsqlEntity that = (VwImpSystemAllsqlEntity) o;
        return Objects.equals(dbKindFull, that.dbKindFull) && Objects.equals(dbConstr, that.dbConstr) && Objects.equals(dbUser, that.dbUser) && Objects.equals(dbPass, that.dbPass) && Objects.equals(sysid, that.sysid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbKindFull, dbConstr, dbUser, dbPass, sysid);
    }
}
