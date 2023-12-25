package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_PARAM", schema = "STG01", catalog = "")
public class VwImpParamEntity {
    @Basic
    @Column(name = "PARAM_SOU", nullable = true, length = 1)
    private String paramSou;
    @Basic
    @Column(name = "PARAM_KIND_0", nullable = true, length = 255)
    private String paramKind0;
    @Basic
    @Column(name = "PARAM_KIND", nullable = true, length = 258)
    private String paramKind;
    @Basic
    @Column(name = "PARAM_NAME", nullable = true, length = 2000)
    private String paramName;
    @Basic
    @Column(name = "PARAM_REMARK", nullable = true, length = 4000)
    private String paramRemark;
    @Basic
    @Column(name = "PARAM_VALUE", nullable = true, length = 4000)
    private String paramValue;

    public String getParamSou() {
        return paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public String getParamKind0() {
        return paramKind0;
    }

    public void setParamKind0(String paramKind0) {
        this.paramKind0 = paramKind0;
    }

    public String getParamKind() {
        return paramKind;
    }

    public void setParamKind(String paramKind) {
        this.paramKind = paramKind;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getParamRemark() {
        return paramRemark;
    }

    public void setParamRemark(String paramRemark) {
        this.paramRemark = paramRemark;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpParamEntity that = (VwImpParamEntity) o;
        return Objects.equals(paramSou, that.paramSou) && Objects.equals(paramKind0, that.paramKind0) && Objects.equals(paramKind, that.paramKind) && Objects.equals(paramName, that.paramName) && Objects.equals(paramRemark, that.paramRemark) && Objects.equals(paramValue, that.paramValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paramSou, paramKind0, paramKind, paramName, paramRemark, paramValue);
    }
}
