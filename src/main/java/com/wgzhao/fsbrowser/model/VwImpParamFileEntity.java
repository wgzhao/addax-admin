package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_PARAM_FILE", schema = "STG01", catalog = "")
public class VwImpParamFileEntity {
    @Basic
    @Column(name = "PARAM_FILE", nullable = true, length = 14)
    private String paramFile;
    @Basic
    @Column(name = "PARAM_CONTENT", nullable = true)
    private String paramContent;

    public String getParamFile() {
        return paramFile;
    }

    public void setParamFile(String paramFile) {
        this.paramFile = paramFile;
    }

    public String getParamContent() {
        return paramContent;
    }

    public void setParamContent(String paramContent) {
        this.paramContent = paramContent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpParamFileEntity that = (VwImpParamFileEntity) o;
        return Objects.equals(paramFile, that.paramFile) && Objects.equals(paramContent, that.paramContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paramFile, paramContent);
    }
}
