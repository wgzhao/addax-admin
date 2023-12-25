package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL_COLTYPE", schema = "STG01", catalog = "")
public class VwImpEtlColtypeEntity {
    @Basic
    @Column(name = "COLTYPE", nullable = false, length = 255)
    private String coltype;
    @Basic
    @Column(name = "HIVE_TYPE", nullable = true, length = 2000)
    private String hiveType;
    @Basic
    @Column(name = "REMARK", nullable = true, length = 4000)
    private String remark;

    public String getColtype() {
        return coltype;
    }

    public void setColtype(String coltype) {
        this.coltype = coltype;
    }

    public String getHiveType() {
        return hiveType;
    }

    public void setHiveType(String hiveType) {
        this.hiveType = hiveType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpEtlColtypeEntity that = (VwImpEtlColtypeEntity) o;
        return Objects.equals(coltype, that.coltype) && Objects.equals(hiveType, that.hiveType) && Objects.equals(remark, that.remark);
    }

    @Override
    public int hashCode() {
        return Objects.hash(coltype, hiveType, remark);
    }
}
