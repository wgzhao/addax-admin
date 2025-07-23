package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.sql.Date;

@Entity
@Table(name = "vw_imp_etl_overprec")
@Immutable
@Setter
@Getter
public class VwImpEtlOverprec {

    @Id
    @Column(name = "sysname", nullable = true, length = 275)
    private String sysname;
    @Column(name = "db_start", nullable = true, length = 32)
    private String dbStart;
    @Column(name = "db_start_dt", nullable = true, length = 2000)
    private String dbStartDt;
    @Column(name = "total_cnt", nullable = true, precision = 0)
    private Integer totalCnt;
    @Column(name = "over_cnt", nullable = true, precision = 0)
    private Integer overCnt;
    @Column(name = "over_prec", nullable = true, precision = 0)
    private Float overPrec;
    @Column(name = "run_cnt", nullable = true, precision = 0)
    private Integer runCnt;
    @Column(name = "err_cnt", nullable = true, precision = 0)
    private Integer errCnt;
    @Column(name = "no_cnt", nullable = true, precision = 0)
    private Integer noCnt;
    @Column(name = "wait_cnt", nullable = true, precision = 0)
    private Integer waitCnt;
    @Column(name = "start_time_ltd", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeLtd;
    @Column(name = "end_time_ltd", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeLtd;
    @Column(name = "start_time_td", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeTd;
    @Column(name = "end_time_td", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeTd;
    @Column(name = "start_time_r", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeR;
    @Column(name = "end_time_r", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeR;
    @Column(name = "runtime_ltd", nullable = true, precision = 0)
    private Integer runtimeLtd;
    @Column(name = "runtime_td", nullable = true, precision = 0)
    private Integer runtimeTd;
    @Column(name = "runtime_r", nullable = true, precision = 0)
    private Integer runtimeR;
}
