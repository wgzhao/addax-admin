package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_UPDT_RDS", schema = "STG01", catalog = "")
public class VwUpdtRdsEntity {
    @Basic
    @Column(name = "RDS", nullable = true, length = 4000)
    private String rds;

    public String getRds() {
        return rds;
    }

    public void setRds(String rds) {
        this.rds = rds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwUpdtRdsEntity that = (VwUpdtRdsEntity) o;
        return Objects.equals(rds, that.rds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rds);
    }
}
