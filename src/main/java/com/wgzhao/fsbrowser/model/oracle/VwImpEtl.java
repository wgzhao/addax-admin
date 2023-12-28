package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.sql.Date;

@Entity
@Table(name = "VW_IMP_ETL")
@Setter
@Getter
public class VwImpEtl {

    @Column(name = "WKF")
    private String wkf;

    @Id
    @Column(name = "SYSID")
    private String sysid;

    @Column(name = "SYS_NAME")
    private String sysName;

    @Column(name = "SYSNAME")
    private String sysname;

    @Column(name = "DB_START")
    private String dbStart;

    @Column(name = "DB_START_DT")
    private String dbStartDt;

    @Column(name = "SOU_DB_CONN")
    private String souDbConn;

    @Column(name = "SOU_DB_KIND")
    private String souDbKind;

    @Column(name = "SOU_DB_CONSTR")
    private String souDbConstr;

    @Column(name = "SOU_DB_USER")
    private String souDbUser;

    @Column(name = "SOU_DB_PASS")
    private String souDbPass;

    @Column(name = "SOU_DB_CONF")
    private String souDbConf;

    @Column(name = "SOU_OWNER")
    private String souOwner;

    @Column(name = "SOU_TABLENAME")
    private String souTablename;

    @Column(name = "SOU_FILTER")
    private String souFilter;

    @Column(name = "SOU_SPLIT")
    private String souSplit;

    @Column(name = "DEST_OWNER")
    private String destOwner;

    @Column(name = "DEST_TABLENAME")
    private String destTablename;

    @Column(name = "DEST_PART_KIND")
    private String destPartKind;

    @Column(name = "FLAG")
    private String flag;

    @Column(name = "PARAM_SOU")
    private String paramSou;

    @Column(name = "BUPDATE")
    private String bupdate;

    @Column(name = "BCREATE")
    private String bcreate;

    @Column(name = "ETL_KIND")
    private String etlKind;

    @Column(name = "RETRY_CNT")
    private Boolean retryCnt;

    @Column(name = "START_TIME")
    private Date startTime;

    @Column(name = "END_TIME")
    private Date endTime;

    @Column(name = "RUNTIME")
    private Integer runtime;

    @Column(name = "RUNTIME_ADD")
    private Integer runtimeAdd;

    @Column(name = "TID")
    private String tid;

    @Column(name = "RID")
    private Object rid;

    @Column(name = "SPNAME")
    private String spname;

    @Column(name = "DEST")
    private String dest;

    @Column(name = "BPREVIEW")
    private String bpreview;

    @Column(name = "BTDH")
    private String btdh;

    @Column(name = "BREALTIME")
    private BigInteger brealtime;

    @Column(name = "REALTIME_INTERVAL")
    private Short realtimeInterval;

    @Column(name = "REALTIME_INTERVAL_RANGE")
    private String realtimeIntervalRange;

    @Column(name = "REALTIME_TASKGROUP")
    private String realtimeTaskgroup;

    @Column(name = "REALTIME_FIXED")
    private String realtimeFixed;

    @Column(name = "BAFTER_RETRY")
    private BigInteger bafterRetry;

    @Column(name = "AFTER_RETRY_FIXED")
    private String afterRetryFixed;

    @Column(name = "AFTER_RETRY_PNTYPE")
    private String afterRetryPntype;

    @Column(name = "BRUN")
    private BigInteger brun;

    @Column(name = "BVALID")
    private BigInteger bvalid;

    @Column(name = "BCJ")
    private BigInteger bcj;

    @Column(name = "JOBKIND")
    private String jobkind;
}
