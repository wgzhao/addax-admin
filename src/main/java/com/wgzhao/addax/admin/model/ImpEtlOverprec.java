package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.sql.Date;

@Entity
@Data
@Table(name="imp_etl_overprec")
public class ImpEtlOverprec {

    @Id
    @Column(name="sysname")
    private String sysname;

    @Column(name="db_start")
    private String dbStart;

    private String dbStartDt;

    private Integer totalCnt;

    private Integer overCnt;

    private Float overPrec;

    private Integer runCnt;

    private Integer errCnt;

    private Integer noCnt;

    @Column(name="wait_cnt")
    private Integer waitCnt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeLtd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeLtd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeTd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeTd;

    @Column(name="start_time_r")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeR;

    @Column(name="end_time_r")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeR;

    private Integer runtimeLtd;

    private Integer runtimeTd;

    @Column(name="runtime_r")
    private Integer runtimeR;
}
