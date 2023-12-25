package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL_SOUTAB", schema = "STG01", catalog = "")
public class VwImpEtlSoutabEntity {
    @Basic
    @Column(name = "KIND", nullable = true, length = 3)
    private String kind;
    @Basic
    @Column(name = "SOU_DB_CONN", nullable = true, length = 161)
    private String souDbConn;
    @Basic
    @Column(name = "COL_JSON", nullable = true)
    private String colJson;

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSouDbConn() {
        return souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getColJson() {
        return colJson;
    }

    public void setColJson(String colJson) {
        this.colJson = colJson;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpEtlSoutabEntity that = (VwImpEtlSoutabEntity) o;
        return Objects.equals(kind, that.kind) && Objects.equals(souDbConn, that.souDbConn) && Objects.equals(colJson, that.colJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, souDbConn, colJson);
    }
}
