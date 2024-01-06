package com.wgzhao.addax.admin.model.oracle;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    private Float overPrec;

    private Integer runCnt;

    private Integer errCnt;

    private Integer noCnt;

    @Column(name="WAIT_CNT")
    private Integer waitCnt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeLtd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeLtd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeTd;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeTd;

    @Column(name="START_TIME_R")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeR;

    @Column(name="END_TIME_R")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeR;

    private Integer runtimeLtd;

    private Integer runtimeTd;

    @Column(name="RUNTIME_R")
    private Integer runtimeR;
}
