package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_PLAN_ALL", schema = "STG01", catalog = "")
public class VwImpPlanAllEntity {
    @Basic
    @Column(name = "PN_TYPE", nullable = true, length = 1)
    private String pnType;
    @Basic
    @Column(name = "DT_FULL", nullable = true, length = 2000)
    private String dtFull;
    @Basic
    @Column(name = "PN_TYPE_NAME", nullable = true, length = 4000)
    private String pnTypeName;
    @Basic
    @Column(name = "SPNAME", nullable = true, length = 4000)
    private String spname;
    @Basic
    @Column(name = "PN_FLAG", nullable = true, length = 1)
    private String pnFlag;
    @Basic
    @Column(name = "C1", nullable = true, length = 4000)
    private String c1;
    @Basic
    @Column(name = "C1F", nullable = true, length = 4)
    private String c1F;
    @Basic
    @Column(name = "C2", nullable = true, length = 4000)
    private String c2;
    @Basic
    @Column(name = "C2F", nullable = true, length = 4)
    private String c2F;
    @Basic
    @Column(name = "C3", nullable = true, length = 4000)
    private String c3;
    @Basic
    @Column(name = "C3F", nullable = true, length = 4)
    private String c3F;
    @Basic
    @Column(name = "C4", nullable = true, length = 4000)
    private String c4;
    @Basic
    @Column(name = "C4F", nullable = true, length = 4)
    private String c4F;
    @Basic
    @Column(name = "C5", nullable = true, length = 4000)
    private String c5;
    @Basic
    @Column(name = "C5F", nullable = true, length = 4)
    private String c5F;
    @Basic
    @Column(name = "C6", nullable = true, length = 4000)
    private String c6;
    @Basic
    @Column(name = "C6F", nullable = true, length = 4)
    private String c6F;

    public String getPnType() {
        return pnType;
    }

    public void setPnType(String pnType) {
        this.pnType = pnType;
    }

    public String getDtFull() {
        return dtFull;
    }

    public void setDtFull(String dtFull) {
        this.dtFull = dtFull;
    }

    public String getPnTypeName() {
        return pnTypeName;
    }

    public void setPnTypeName(String pnTypeName) {
        this.pnTypeName = pnTypeName;
    }

    public String getSpname() {
        return spname;
    }

    public void setSpname(String spname) {
        this.spname = spname;
    }

    public String getPnFlag() {
        return pnFlag;
    }

    public void setPnFlag(String pnFlag) {
        this.pnFlag = pnFlag;
    }

    public String getC1() {
        return c1;
    }

    public void setC1(String c1) {
        this.c1 = c1;
    }

    public String getC1F() {
        return c1F;
    }

    public void setC1F(String c1F) {
        this.c1F = c1F;
    }

    public String getC2() {
        return c2;
    }

    public void setC2(String c2) {
        this.c2 = c2;
    }

    public String getC2F() {
        return c2F;
    }

    public void setC2F(String c2F) {
        this.c2F = c2F;
    }

    public String getC3() {
        return c3;
    }

    public void setC3(String c3) {
        this.c3 = c3;
    }

    public String getC3F() {
        return c3F;
    }

    public void setC3F(String c3F) {
        this.c3F = c3F;
    }

    public String getC4() {
        return c4;
    }

    public void setC4(String c4) {
        this.c4 = c4;
    }

    public String getC4F() {
        return c4F;
    }

    public void setC4F(String c4F) {
        this.c4F = c4F;
    }

    public String getC5() {
        return c5;
    }

    public void setC5(String c5) {
        this.c5 = c5;
    }

    public String getC5F() {
        return c5F;
    }

    public void setC5F(String c5F) {
        this.c5F = c5F;
    }

    public String getC6() {
        return c6;
    }

    public void setC6(String c6) {
        this.c6 = c6;
    }

    public String getC6F() {
        return c6F;
    }

    public void setC6F(String c6F) {
        this.c6F = c6F;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpPlanAllEntity that = (VwImpPlanAllEntity) o;
        return Objects.equals(pnType, that.pnType) && Objects.equals(dtFull, that.dtFull) && Objects.equals(pnTypeName, that.pnTypeName) && Objects.equals(spname, that.spname) && Objects.equals(pnFlag, that.pnFlag) && Objects.equals(c1, that.c1) && Objects.equals(c1F, that.c1F) && Objects.equals(c2, that.c2) && Objects.equals(c2F, that.c2F) && Objects.equals(c3, that.c3) && Objects.equals(c3F, that.c3F) && Objects.equals(c4, that.c4) && Objects.equals(c4F, that.c4F) && Objects.equals(c5, that.c5) && Objects.equals(c5F, that.c5F) && Objects.equals(c6, that.c6) && Objects.equals(c6F, that.c6F);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pnType, dtFull, pnTypeName, spname, pnFlag, c1, c1F, c2, c2F, c3, c3F, c4, c4F, c5, c5F, c6, c6F);
    }
}
