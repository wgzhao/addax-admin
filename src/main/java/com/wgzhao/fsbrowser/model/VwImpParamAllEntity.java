package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_PARAM_ALL", schema = "STG01", catalog = "")
public class VwImpParamAllEntity {
    @Basic
    @Column(name = "CD", nullable = true, length = 4000)
    private String cd;
    @Basic
    @Column(name = "CM0", nullable = true, length = 4000)
    private String cm0;
    @Basic
    @Column(name = "CM1", nullable = true, length = 4000)
    private String cm1;
    @Basic
    @Column(name = "CQ0", nullable = true, length = 4000)
    private String cq0;
    @Basic
    @Column(name = "CQ1", nullable = true, length = 4000)
    private String cq1;
    @Basic
    @Column(name = "CW0", nullable = true, length = 4000)
    private String cw0;
    @Basic
    @Column(name = "CW1", nullable = true, length = 4000)
    private String cw1;
    @Basic
    @Column(name = "CWM", nullable = true, length = 4000)
    private String cwm;
    @Basic
    @Column(name = "CWS", nullable = true, length = 4000)
    private String cws;
    @Basic
    @Column(name = "CY0", nullable = true, length = 4000)
    private String cy0;
    @Basic
    @Column(name = "CY1", nullable = true, length = 4000)
    private String cy1;
    @Basic
    @Column(name = "L10TD", nullable = true, length = 4000)
    private String l10Td;
    @Basic
    @Column(name = "L180TD", nullable = true, length = 4000)
    private String l180Td;
    @Basic
    @Column(name = "L20TD", nullable = true, length = 4000)
    private String l20Td;
    @Basic
    @Column(name = "L2M0", nullable = true, length = 4000)
    private String l2M0;
    @Basic
    @Column(name = "L2M1", nullable = true, length = 4000)
    private String l2M1;
    @Basic
    @Column(name = "L2Q0", nullable = true, length = 4000)
    private String l2Q0;
    @Basic
    @Column(name = "L2Q1", nullable = true, length = 4000)
    private String l2Q1;
    @Basic
    @Column(name = "L2TM", nullable = true, length = 4000)
    private String l2Tm;
    @Basic
    @Column(name = "L2TY", nullable = true, length = 4000)
    private String l2Ty;
    @Basic
    @Column(name = "L2Y0", nullable = true, length = 4000)
    private String l2Y0;
    @Basic
    @Column(name = "L2Y1", nullable = true, length = 4000)
    private String l2Y1;
    @Basic
    @Column(name = "L30TD", nullable = true, length = 4000)
    private String l30Td;
    @Basic
    @Column(name = "L40TD", nullable = true, length = 4000)
    private String l40Td;
    @Basic
    @Column(name = "L5TD", nullable = true, length = 4000)
    private String l5Td;
    @Basic
    @Column(name = "L5TDM", nullable = true, length = 4000)
    private String l5Tdm;
    @Basic
    @Column(name = "L60TD", nullable = true, length = 4000)
    private String l60Td;
    @Basic
    @Column(name = "L90TD", nullable = true, length = 4000)
    private String l90Td;
    @Basic
    @Column(name = "LD180", nullable = true, length = 4000)
    private String ld180;
    @Basic
    @Column(name = "LD180T", nullable = true, length = 4000)
    private String ld180T;
    @Basic
    @Column(name = "LD30", nullable = true, length = 4000)
    private String ld30;
    @Basic
    @Column(name = "LD30T", nullable = true, length = 4000)
    private String ld30T;
    @Basic
    @Column(name = "LD365", nullable = true, length = 4000)
    private String ld365;
    @Basic
    @Column(name = "LD365T", nullable = true, length = 4000)
    private String ld365T;
    @Basic
    @Column(name = "LD730", nullable = true, length = 4000)
    private String ld730;
    @Basic
    @Column(name = "LD730T", nullable = true, length = 4000)
    private String ld730T;
    @Basic
    @Column(name = "LD800T", nullable = true, length = 4000)
    private String ld800T;
    @Basic
    @Column(name = "LD90", nullable = true, length = 4000)
    private String ld90;
    @Basic
    @Column(name = "LD90T", nullable = true, length = 4000)
    private String ld90T;
    @Basic
    @Column(name = "LM0", nullable = true, length = 4000)
    private String lm0;
    @Basic
    @Column(name = "LM1", nullable = true, length = 4000)
    private String lm1;
    @Basic
    @Column(name = "LQ0", nullable = true, length = 4000)
    private String lq0;
    @Basic
    @Column(name = "LQ1", nullable = true, length = 4000)
    private String lq1;
    @Basic
    @Column(name = "LTD", nullable = true, length = 4000)
    private String ltd;
    @Basic
    @Column(name = "LTM", nullable = true, length = 4000)
    private String ltm;
    @Basic
    @Column(name = "LTQ", nullable = true, length = 4000)
    private String ltq;
    @Basic
    @Column(name = "LTY", nullable = true, length = 4000)
    private String lty;
    @Basic
    @Column(name = "LW0", nullable = true, length = 4000)
    private String lw0;
    @Basic
    @Column(name = "LW1", nullable = true, length = 4000)
    private String lw1;
    @Basic
    @Column(name = "LY0", nullable = true, length = 4000)
    private String ly0;
    @Basic
    @Column(name = "LY1", nullable = true, length = 4000)
    private String ly1;
    @Basic
    @Column(name = "LYTD", nullable = true, length = 4000)
    private String lytd;
    @Basic
    @Column(name = "NTD", nullable = true, length = 4000)
    private String ntd;
    @Basic
    @Column(name = "TD", nullable = true, length = 4000)
    private String td;
    @Basic
    @Column(name = "TF", nullable = true, length = 4000)
    private String tf;
    @Basic
    @Column(name = "TM", nullable = true, length = 4000)
    private String tm;
    @Basic
    @Column(name = "TQ", nullable = true, length = 4000)
    private String tq;
    @Basic
    @Column(name = "TY", nullable = true, length = 4000)
    private String ty;

    public String getCd() {
        return cd;
    }

    public void setCd(String cd) {
        this.cd = cd;
    }

    public String getCm0() {
        return cm0;
    }

    public void setCm0(String cm0) {
        this.cm0 = cm0;
    }

    public String getCm1() {
        return cm1;
    }

    public void setCm1(String cm1) {
        this.cm1 = cm1;
    }

    public String getCq0() {
        return cq0;
    }

    public void setCq0(String cq0) {
        this.cq0 = cq0;
    }

    public String getCq1() {
        return cq1;
    }

    public void setCq1(String cq1) {
        this.cq1 = cq1;
    }

    public String getCw0() {
        return cw0;
    }

    public void setCw0(String cw0) {
        this.cw0 = cw0;
    }

    public String getCw1() {
        return cw1;
    }

    public void setCw1(String cw1) {
        this.cw1 = cw1;
    }

    public String getCwm() {
        return cwm;
    }

    public void setCwm(String cwm) {
        this.cwm = cwm;
    }

    public String getCws() {
        return cws;
    }

    public void setCws(String cws) {
        this.cws = cws;
    }

    public String getCy0() {
        return cy0;
    }

    public void setCy0(String cy0) {
        this.cy0 = cy0;
    }

    public String getCy1() {
        return cy1;
    }

    public void setCy1(String cy1) {
        this.cy1 = cy1;
    }

    public String getL10Td() {
        return l10Td;
    }

    public void setL10Td(String l10Td) {
        this.l10Td = l10Td;
    }

    public String getL180Td() {
        return l180Td;
    }

    public void setL180Td(String l180Td) {
        this.l180Td = l180Td;
    }

    public String getL20Td() {
        return l20Td;
    }

    public void setL20Td(String l20Td) {
        this.l20Td = l20Td;
    }

    public String getL2M0() {
        return l2M0;
    }

    public void setL2M0(String l2M0) {
        this.l2M0 = l2M0;
    }

    public String getL2M1() {
        return l2M1;
    }

    public void setL2M1(String l2M1) {
        this.l2M1 = l2M1;
    }

    public String getL2Q0() {
        return l2Q0;
    }

    public void setL2Q0(String l2Q0) {
        this.l2Q0 = l2Q0;
    }

    public String getL2Q1() {
        return l2Q1;
    }

    public void setL2Q1(String l2Q1) {
        this.l2Q1 = l2Q1;
    }

    public String getL2Tm() {
        return l2Tm;
    }

    public void setL2Tm(String l2Tm) {
        this.l2Tm = l2Tm;
    }

    public String getL2Ty() {
        return l2Ty;
    }

    public void setL2Ty(String l2Ty) {
        this.l2Ty = l2Ty;
    }

    public String getL2Y0() {
        return l2Y0;
    }

    public void setL2Y0(String l2Y0) {
        this.l2Y0 = l2Y0;
    }

    public String getL2Y1() {
        return l2Y1;
    }

    public void setL2Y1(String l2Y1) {
        this.l2Y1 = l2Y1;
    }

    public String getL30Td() {
        return l30Td;
    }

    public void setL30Td(String l30Td) {
        this.l30Td = l30Td;
    }

    public String getL40Td() {
        return l40Td;
    }

    public void setL40Td(String l40Td) {
        this.l40Td = l40Td;
    }

    public String getL5Td() {
        return l5Td;
    }

    public void setL5Td(String l5Td) {
        this.l5Td = l5Td;
    }

    public String getL5Tdm() {
        return l5Tdm;
    }

    public void setL5Tdm(String l5Tdm) {
        this.l5Tdm = l5Tdm;
    }

    public String getL60Td() {
        return l60Td;
    }

    public void setL60Td(String l60Td) {
        this.l60Td = l60Td;
    }

    public String getL90Td() {
        return l90Td;
    }

    public void setL90Td(String l90Td) {
        this.l90Td = l90Td;
    }

    public String getLd180() {
        return ld180;
    }

    public void setLd180(String ld180) {
        this.ld180 = ld180;
    }

    public String getLd180T() {
        return ld180T;
    }

    public void setLd180T(String ld180T) {
        this.ld180T = ld180T;
    }

    public String getLd30() {
        return ld30;
    }

    public void setLd30(String ld30) {
        this.ld30 = ld30;
    }

    public String getLd30T() {
        return ld30T;
    }

    public void setLd30T(String ld30T) {
        this.ld30T = ld30T;
    }

    public String getLd365() {
        return ld365;
    }

    public void setLd365(String ld365) {
        this.ld365 = ld365;
    }

    public String getLd365T() {
        return ld365T;
    }

    public void setLd365T(String ld365T) {
        this.ld365T = ld365T;
    }

    public String getLd730() {
        return ld730;
    }

    public void setLd730(String ld730) {
        this.ld730 = ld730;
    }

    public String getLd730T() {
        return ld730T;
    }

    public void setLd730T(String ld730T) {
        this.ld730T = ld730T;
    }

    public String getLd800T() {
        return ld800T;
    }

    public void setLd800T(String ld800T) {
        this.ld800T = ld800T;
    }

    public String getLd90() {
        return ld90;
    }

    public void setLd90(String ld90) {
        this.ld90 = ld90;
    }

    public String getLd90T() {
        return ld90T;
    }

    public void setLd90T(String ld90T) {
        this.ld90T = ld90T;
    }

    public String getLm0() {
        return lm0;
    }

    public void setLm0(String lm0) {
        this.lm0 = lm0;
    }

    public String getLm1() {
        return lm1;
    }

    public void setLm1(String lm1) {
        this.lm1 = lm1;
    }

    public String getLq0() {
        return lq0;
    }

    public void setLq0(String lq0) {
        this.lq0 = lq0;
    }

    public String getLq1() {
        return lq1;
    }

    public void setLq1(String lq1) {
        this.lq1 = lq1;
    }

    public String getLtd() {
        return ltd;
    }

    public void setLtd(String ltd) {
        this.ltd = ltd;
    }

    public String getLtm() {
        return ltm;
    }

    public void setLtm(String ltm) {
        this.ltm = ltm;
    }

    public String getLtq() {
        return ltq;
    }

    public void setLtq(String ltq) {
        this.ltq = ltq;
    }

    public String getLty() {
        return lty;
    }

    public void setLty(String lty) {
        this.lty = lty;
    }

    public String getLw0() {
        return lw0;
    }

    public void setLw0(String lw0) {
        this.lw0 = lw0;
    }

    public String getLw1() {
        return lw1;
    }

    public void setLw1(String lw1) {
        this.lw1 = lw1;
    }

    public String getLy0() {
        return ly0;
    }

    public void setLy0(String ly0) {
        this.ly0 = ly0;
    }

    public String getLy1() {
        return ly1;
    }

    public void setLy1(String ly1) {
        this.ly1 = ly1;
    }

    public String getLytd() {
        return lytd;
    }

    public void setLytd(String lytd) {
        this.lytd = lytd;
    }

    public String getNtd() {
        return ntd;
    }

    public void setNtd(String ntd) {
        this.ntd = ntd;
    }

    public String getTd() {
        return td;
    }

    public void setTd(String td) {
        this.td = td;
    }

    public String getTf() {
        return tf;
    }

    public void setTf(String tf) {
        this.tf = tf;
    }

    public String getTm() {
        return tm;
    }

    public void setTm(String tm) {
        this.tm = tm;
    }

    public String getTq() {
        return tq;
    }

    public void setTq(String tq) {
        this.tq = tq;
    }

    public String getTy() {
        return ty;
    }

    public void setTy(String ty) {
        this.ty = ty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpParamAllEntity that = (VwImpParamAllEntity) o;
        return Objects.equals(cd, that.cd) && Objects.equals(cm0, that.cm0) && Objects.equals(cm1, that.cm1) && Objects.equals(cq0, that.cq0) && Objects.equals(cq1, that.cq1) && Objects.equals(cw0, that.cw0) && Objects.equals(cw1, that.cw1) && Objects.equals(cwm, that.cwm) && Objects.equals(cws, that.cws) && Objects.equals(cy0, that.cy0) && Objects.equals(cy1, that.cy1) && Objects.equals(l10Td, that.l10Td) && Objects.equals(l180Td, that.l180Td) && Objects.equals(l20Td, that.l20Td) && Objects.equals(l2M0, that.l2M0) && Objects.equals(l2M1, that.l2M1) && Objects.equals(l2Q0, that.l2Q0) && Objects.equals(l2Q1, that.l2Q1) && Objects.equals(l2Tm, that.l2Tm) && Objects.equals(l2Ty, that.l2Ty) && Objects.equals(l2Y0, that.l2Y0) && Objects.equals(l2Y1, that.l2Y1) && Objects.equals(l30Td, that.l30Td) && Objects.equals(l40Td, that.l40Td) && Objects.equals(l5Td, that.l5Td) && Objects.equals(l5Tdm, that.l5Tdm) && Objects.equals(l60Td, that.l60Td) && Objects.equals(l90Td, that.l90Td) && Objects.equals(ld180, that.ld180) && Objects.equals(ld180T, that.ld180T) && Objects.equals(ld30, that.ld30) && Objects.equals(ld30T, that.ld30T) && Objects.equals(ld365, that.ld365) && Objects.equals(ld365T, that.ld365T) && Objects.equals(ld730, that.ld730) && Objects.equals(ld730T, that.ld730T) && Objects.equals(ld800T, that.ld800T) && Objects.equals(ld90, that.ld90) && Objects.equals(ld90T, that.ld90T) && Objects.equals(lm0, that.lm0) && Objects.equals(lm1, that.lm1) && Objects.equals(lq0, that.lq0) && Objects.equals(lq1, that.lq1) && Objects.equals(ltd, that.ltd) && Objects.equals(ltm, that.ltm) && Objects.equals(ltq, that.ltq) && Objects.equals(lty, that.lty) && Objects.equals(lw0, that.lw0) && Objects.equals(lw1, that.lw1) && Objects.equals(ly0, that.ly0) && Objects.equals(ly1, that.ly1) && Objects.equals(lytd, that.lytd) && Objects.equals(ntd, that.ntd) && Objects.equals(td, that.td) && Objects.equals(tf, that.tf) && Objects.equals(tm, that.tm) && Objects.equals(tq, that.tq) && Objects.equals(ty, that.ty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cd, cm0, cm1, cq0, cq1, cw0, cw1, cwm, cws, cy0, cy1, l10Td, l180Td, l20Td, l2M0, l2M1, l2Q0, l2Q1, l2Tm, l2Ty, l2Y0, l2Y1, l30Td, l40Td, l5Td, l5Tdm, l60Td, l90Td, ld180, ld180T, ld30, ld30T, ld365, ld365T, ld730, ld730T, ld800T, ld90, ld90T, lm0, lm1, lq0, lq1, ltd, ltm, ltq, lty, lw0, lw1, ly0, ly1, lytd, ntd, td, tf, tm, tq, ty);
    }
}
