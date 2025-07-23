package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vw_imp_plan_all")
@Setter
@Getter
public class VwImpPlanAll {

    @Column(name = "pn_type")
    private String pnType;

    @Column(name = "dt_full")
    private String dtFull;

    @Column(name = "pn_type_name")
    private String pnTypeName;

    @Id
    @Column(name = "spname")
    private String spname;

    @Column(name = "pn_flag")
    private String pnFlag;

    @Column(name = "c1")
    private String c1;

    @Column(name = "c1f")
    private String c1F;

    @Column(name = "c2")
    private String c2;

    @Column(name = "c2f")
    private String c2F;

    @Column(name = "c3")
    private String c3;

    @Column(name = "c3f")
    private String c3F;

    @Column(name = "c4")
    private String c4;

    @Column(name = "c4f")
    private String c4F;

    @Column(name = "c5")
    private String c5;

    @Column(name = "c5f")
    private String c5F;

    @Column(name = "c6")
    private String c6;

    @Column(name = "c6f")
    private String c6F;
}
