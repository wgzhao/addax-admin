package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.sql.Date;

@Entity
@Table(name = "VW_IMP_TASKGROUP_DETAIL")
@Setter
@Getter
public class VwImpTaskgroupDetail {

    @Id
    @Column(name = "TASK_GROUP")
    private String taskGroup;

    @Column(name = "KIND")
    private String kind;

    @Column(name = "ALLCNT")
    private Integer allcnt;

    @Column(name = "YCNT")
    private Integer ycnt;

    @Column(name = "START_TIME")
    private Date startTime;

    @Column(name = "END_TIME")
    private Date endTime;

    @Column(name = "RUNTIME")
    private Integer runtime;

    @Column(name = "RCNT")
    private Integer rcnt;

    @Column(name = "START_TIME_R")
    private Date startTimeR;

    @Column(name = "NCNT")
    private Integer ncnt;

    @Column(name = "ECNT")
    private Integer ecnt;

    @Column(name = "PREC")
    private Float prec;

    @Column(name = "KIND2")
    private String kind2;

    @Column(name = "TASK_GROUP2")
    private String taskGroup2;

    @Column(name = "DS_NAME")
    private String dsName;

    @Column(name = "FLAG2")
    private String flag2;

    @Column(name = "START_TIME2")
    private Date startTime2;

    @Column(name = "END_TIME2")
    private Date endTime2;

    @Column(name = "BFLAG")
    private String bflag;

    @Column(name = "FLAG_TIME")
    private Date flagTime;

    @Column(name = "ERRMSG")
    private String errmsg;
}
