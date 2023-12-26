package com.wgzhao.fsbrowser.model.oracle;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL_OVERPREC")
@Immutable
@Setter
@Getter
public class VwImpEtlOverprecEntity {

    @Id
    @Basic
    @Column(name = "SYSNAME", nullable = true, length = 275)
    private String sysname;
    @Basic
    @Column(name = "DB_START", nullable = true, length = 32)
    private String dbStart;
    @Basic
    @Column(name = "DB_START_DT", nullable = true, length = 2000)
    private String dbStartDt;
    @Basic
    @Column(name = "TOTAL_CNT", nullable = true, precision = 0)
    private BigInteger totalCnt;
    @Basic
    @Column(name = "OVER_CNT", nullable = true, precision = 0)
    private BigInteger overCnt;
    @Basic
    @Column(name = "OVER_PREC", nullable = true, precision = 0)
    private BigInteger overPrec;
    @Basic
    @Column(name = "RUN_CNT", nullable = true, precision = 0)
    private BigInteger runCnt;
    @Basic
    @Column(name = "ERR_CNT", nullable = true, precision = 0)
    private BigInteger errCnt;
    @Basic
    @Column(name = "NO_CNT", nullable = true, precision = 0)
    private BigInteger noCnt;
    @Basic
    @Column(name = "WAIT_CNT", nullable = true, precision = 0)
    private BigInteger waitCnt;
    @Basic
    @Column(name = "START_TIME_LTD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeLtd;
    @Basic
    @Column(name = "END_TIME_LTD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeLtd;
    @Basic
    @Column(name = "START_TIME_TD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeTd;
    @Basic
    @Column(name = "END_TIME_TD", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeTd;
    @Basic
    @Column(name = "START_TIME_R", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTimeR;
    @Basic
    @Column(name = "END_TIME_R", nullable = true)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTimeR;
    @Basic
    @Column(name = "RUNTIME_LTD", nullable = true, precision = 0)
    private BigInteger runtimeLtd;
    @Basic
    @Column(name = "RUNTIME_TD", nullable = true, precision = 0)
    private BigInteger runtimeTd;
    @Basic
    @Column(name = "RUNTIME_R", nullable = true, precision = 0)
    private BigInteger runtimeR;
}
