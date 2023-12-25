package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SP_SOURCE", schema = "STG01", catalog = "")
public class VwImpSpSourceEntity {
    @Basic
    @Column(name = "PARENT_ID", nullable = true, length = 32)
    private String parentId;
    @Basic
    @Column(name = "CATE_ID", nullable = true, length = 32)
    private String cateId;
    @Basic
    @Column(name = "CATE_NAME", nullable = true, length = 511)
    private String cateName;
    @Basic
    @Column(name = "CATE_VALUE", nullable = true, precision = 0)
    private BigInteger cateValue;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getCateId() {
        return cateId;
    }

    public void setCateId(String cateId) {
        this.cateId = cateId;
    }

    public String getCateName() {
        return cateName;
    }

    public void setCateName(String cateName) {
        this.cateName = cateName;
    }

    public BigInteger getCateValue() {
        return cateValue;
    }

    public void setCateValue(BigInteger cateValue) {
        this.cateValue = cateValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpSpSourceEntity that = (VwImpSpSourceEntity) o;
        return Objects.equals(parentId, that.parentId) && Objects.equals(cateId, that.cateId) && Objects.equals(cateName, that.cateName) && Objects.equals(cateValue, that.cateValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parentId, cateId, cateName, cateValue);
    }
}
