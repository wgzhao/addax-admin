package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "vw_imp_taskgroup_detail")
@Setter
@Getter
public class VwImpTaskgroupDetail {

    @Id
    @Column(name = "task_group")
    private String taskGroup;

    @Column(name = "kind")
    private String kind;

    @Column(name = "allcnt")
    private Integer allcnt;

    @Column(name = "ycnt")
    private Integer ycnt;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "runtime")
    private Integer runtime;

    @Column(name = "rcnt")
    private Integer rcnt;

    @Column(name = "start_time_r")
    private Date startTimeR;

    @Column(name = "ncnt")
    private Integer ncnt;

    @Column(name = "ecnt")
    private Integer ecnt;

    @Column(name = "prec")
    private Float prec;

    @Column(name = "kind2")
    private String kind2;

    @Column(name = "task_group2")
    private String taskGroup2;

    @Column(name = "ds_name")
    private String dsName;

    @Column(name = "flag2")
    private String flag2;

    @Column(name = "start_time2")
    private Date startTime2;

    @Column(name = "end_time2")
    private Date endTime2;

    @Column(name = "bflag")
    private String bflag;

    @Column(name = "flag_time")
    private Date flagTime;

    @Column(name = "errmsg")
    private String errmsg;
}
