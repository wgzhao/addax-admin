package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.math.BigInteger;
import java.sql.Date;

@Entity
@Table(name = "vw_imp_etl")
@Setter
@Getter
public class VwImpEtl {

    @Column(name = "wkf")
    private String wkf;

    @Column(name = "sysid")
    private String sysid;

    @Column(name = "sys_name")
    private String sysName;

    @Column(name = "sysname")
    private String sysname;

    @Column(name = "db_start")
    private String dbStart;

    @Column(name = "db_start_dt")
    private String dbStartDt;

    @Column(name = "sou_db_conn")
    private String souDbConn;

    @Column(name = "sou_db_kind")
    private String souDbKind;

    @Column(name = "sou_db_constr")
    private String souDbConstr;

    @Column(name = "sou_db_user")
    private String souDbUser;

    @Column(name = "sou_db_pass")
    private String souDbPass;

    @Column(name = "sou_db_conf")
    private String souDbConf;

    @Column(name = "sou_owner")
    private String souOwner;

    @Column(name = "sou_tablename")
    private String souTablename;

    @Column(name = "sou_filter")
    private String souFilter;

    @Column(name = "sou_split")
    private String souSplit;

    @Column(name = "dest_owner")
    private String destOwner;

    @Column(name = "dest_tablename")
    private String destTablename;

    @Column(name = "dest_part_kind")
    private String destPartKind;

    @Column(name = "flag")
    private String flag;

    @Column(name = "param_sou")
    private String paramSou;

    @Column(name = "bupdate")
    private String bupdate;

    @Column(name = "bcreate")
    private String bcreate;

    @Column(name = "etl_kind")
    private String etlKind;

    @Column(name = "retry_cnt")
    private Integer retryCnt;

    @Column(name = "start_time")
    private Date startTime;

    @Column(name = "end_time")
    private Date endTime;

    @Column(name = "runtime")
    private Integer runtime;

    @Column(name = "runtime_add")
    private Integer runtimeAdd;

    @Id
    @Column(name = "tid")
    private String tid;

    @Column(name = "rid")
    private String rid;

    @Column(name = "spname")
    private String spname;

    @Column(name = "dest")
    private String dest;

    @Column(name = "bpreview")
    private String bpreview;

    @Column(name = "btdh")
    private String btdh;

    @Column(name = "brealtime")
    private BigInteger brealtime;

    @Column(name = "realtime_interval")
    private Short realtimeInterval;

    @Column(name = "realtime_interval_range")
    private String realtimeIntervalRange;

    @Column(name = "realtime_taskgroup")
    private String realtimeTaskgroup;

    @Column(name = "realtime_fixed")
    private String realtimeFixed;

    @Column(name = "bafter_retry")
    private BigInteger bafterRetry;

    @Column(name = "after_retry_fixed")
    private String afterRetryFixed;

    @Column(name = "after_retry_pntype")
    private String afterRetryPntype;

    @Column(name = "brun")
    private BigInteger brun;

    @Column(name = "bvalid")
    private BigInteger bvalid;

    @Column(name = "bcj")
    private BigInteger bcj;

    @Column(name = "jobkind")
    private String jobkind;

    // virtual column
    @Formula("upper(spname || dest_owner||'.'||dest_tablename||','||sou_owner||'.'||sou_tablename||','||realtime_taskgroup)")
    private String filterColumn;
}
