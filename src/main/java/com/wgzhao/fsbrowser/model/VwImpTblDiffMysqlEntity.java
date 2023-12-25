package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_TBL_DIFF_MYSQL", schema = "STG01", catalog = "")
public class VwImpTblDiffMysqlEntity {
    @Basic
    @Column(name = "KIND", nullable = true, length = 18)
    private String kind;
    @Basic
    @Column(name = "TID", nullable = true, length = 32)
    private String tid;
    @Basic
    @Column(name = "ALTER_SQL", nullable = true)
    private String alterSql;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getAlterSql() {
        return alterSql;
    }

    public void setAlterSql(String alterSql) {
        this.alterSql = alterSql;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpTblDiffMysqlEntity that = (VwImpTblDiffMysqlEntity) o;
        return Objects.equals(kind, that.kind) && Objects.equals(tid, that.tid) && Objects.equals(alterSql, that.alterSql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, tid, alterSql);
    }
}
