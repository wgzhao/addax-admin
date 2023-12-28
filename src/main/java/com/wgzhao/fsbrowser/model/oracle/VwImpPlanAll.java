package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "VW_IMP_PLAN_ALL")
@Setter
@Getter
public class VwImpPlanAll {

    @Column(name = "PN_TYPE")
    private String pnType;

    @Column(name = "DT_FULL")
    private String dtFull;

    @Column(name = "PN_TYPE_NAME")
    private String pnTypeName;

    @Id
    @Column(name = "SPNAME")
    private String spname;

    @Column(name = "PN_FLAG")
    private String pnFlag;

    @Column(name = "C1")
    private String c1;

    @Column(name = "C1F")
    private String c1F;

    @Column(name = "C2")
    private String c2;

    @Column(name = "C2F")
    private String c2F;

    @Column(name = "C3")
    private String c3;

    @Column(name = "C3F")
    private String c3F;

    @Column(name = "C4")
    private String c4;

    @Column(name = "C4F")
    private String c4F;

    @Column(name = "C5")
    private String c5;

    @Column(name = "C5F")
    private String c5F;

    @Column(name = "C6")
    private String c6;

    @Column(name = "C6F")
    private String c6F;
}
