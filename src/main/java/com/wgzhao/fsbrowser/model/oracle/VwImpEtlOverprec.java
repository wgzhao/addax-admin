package com.wgzhao.fsbrowser.model.oracle;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.sql.Date;

@Entity
@Table(name = "VW_IMP_ETL_OVERPREC")
@Immutable
@Setter
@Getter
public class VwImpEtlOverprec {

    @Id
    @Column(name = "SYSNAME", nullable = true, length = 275)
    private String sysname;
    @Column(name = "DB_START", nullable = true, length = 32)
    private String dbStart;
    @Column(name = "DB_START_DT", nullable = true, length = 2000)
    private String dbStartDt;
    @Column(name = "TOTAL_CNT", nullable = true, precision = 0)
    private Integer totalCnt;
    @Column(name = "OVER_CNT", nullable = true, precision = 0)
    private Integer overCnt;
    @Column(name = "OVER_PREC", nullable = true, precision = 0)
    private Float overPrec;
    @Column(name = "RUN_CNT", nullable = true, precision = 0)
    private Integer runCnt;
    @Column(name = "ERR_CNT", nullable = true, precision = 0)
    private Integer errCnt;
    @Column(name = "NO_CNT", nullable = true, precision = 0)
    private Integer noCnt;
    @Column(name = "WAIT_CNT", nullable = true, precision = 0)
    private Integer waitCnt;
    @Column(name = "START_TIME_LTD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeLtd;
    @Column(name = "END_TIME_LTD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeLtd;
    @Column(name = "START_TIME_TD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeTd;
    @Column(name = "END_TIME_TD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeTd;
    @Column(name = "START_TIME_R", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeR;
    @Column(name = "END_TIME_R", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeR;
    @Column(name = "RUNTIME_LTD", nullable = true, precision = 0)
    private Integer runtimeLtd;
    @Column(name = "RUNTIME_TD", nullable = true, precision = 0)
    private Integer runtimeTd;
    @Column(name = "RUNTIME_R", nullable = true, precision = 0)
    private Integer runtimeR;
}
