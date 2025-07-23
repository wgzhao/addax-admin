package com.wgzhao.addax.admin.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.sql.Date;

@Entity
@Table(name = "vw_imp_ds2")
@Setter
@Getter
public class VwImpDs2 {

    @Id
    @Basic
    @Column(name = "ds_id")
    private String dsId;
    @Basic
    @Column(name = "ds_name")
    private String dsName;
    @Basic
    @Column(name = "dest_sysid")
    private String destSysid;
    @Basic
    @Column(name = "task_group")
    private String taskGroup;
    @Basic
    @Column(name = "param_sou")
    private String paramSou;
    @Basic
    @Column(name = "retry_cnt")
    private Integer retryCnt;
    @Basic
    @Column(name = "run_freq")
    private String runFreq;
    @Basic
    @Column(name = "bvalid")
    private Integer bvalid;
    @Basic
    @Column(name = "flag")
    private String flag;
    @Basic
    @Column(name = "start_time")
    private Date startTime;
    @Basic
    @Column(name = "end_time")
    private Date endTime;
    @Basic
    @Column(name = "runtime")
    private Integer runtime;
    @Basic
    @Column(name = "brun")
    private Integer brun;
    @Basic
    @Column(name = "bdelay")
    private Integer bdelay;
    @Basic
    @Column(name = "bfreq")
    private Integer bfreq;
    @Basic
    @Column(name = "bplan")
    private Integer bplan;
    @Basic
    @Column(name = "pre_sh")
    private String preSh;
    @Basic
    @Column(name = "post_sh")
    private String postSh;
    @Basic
    @Column(name = "pre_sql")
    private String preSql;
    @Basic
    @Column(name = "post_sql")
    private String postSql;
    @Basic
    @Column(name = "init_rds")
    private String initRds;

    @Formula("fn_imp_value('ds_needs',ds_id)")
    private String needs;
}
