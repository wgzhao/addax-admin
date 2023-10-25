package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.sql.Date;

@Entity
@Data
@Table(name="VW_IMP_ETL_OVERPREC")
public class ImpEtlOverprec {

    @Id
    @Column(name="SYSNAME")
    private String sysname;

    @Column(name="DB_START")
    private String dbStart;
    private String dbStartDt;
    private Integer totalCnt;
    private Integer overCnt;
    private Integer overPrec;
    private Integer runCnt;
    private Integer errCnt;
    private Integer noCnt;
    private Integer waitCnt;
    private Date startTimeLtd;
    private Date endTimeLtd;
    private Date startTimeTd;
    private Date endTimeTd;

    @Column(name="START_TIME_R")
    private Date startTimeR;

    @Column(name="END_TIME_R")
    private Date endTimeR;
    private Integer runtimeLtd;
    private Integer runtimeTd;
    @Column(name="RUNTIME_R")
    private Integer runtimeR;
}
