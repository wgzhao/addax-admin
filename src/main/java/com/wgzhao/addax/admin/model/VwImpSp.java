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
@Table(name = "vw_imp_sp")
@Setter
@Getter
public class VwImpSp {
    @Basic
    @Column(name = "sp_owner")
    private String spOwner;
    @Basic
    @Column(name = "sp_name")
    private String spName;
    @Basic
    @Column(name = "sp_id")
    private String spId;

    @Id
    @Basic
    @Column(name = "rid")
    private String rid;
    @Basic
    @Column(name = "flag")
    private String flag;
    @Basic
    @Column(name = "run_freq")
    private String runFreq;
    @Basic
    @Column(name = "start_time")
    private Date startTime;
    @Basic
    @Column(name = "end_time")
    private Date endTime;
    @Basic
    @Column(name = "retry_cnt")
    private Byte retryCnt;
    @Basic
    @Column(name = "runtime")
    private Integer runtime;
    @Basic
    @Column(name = "realtime_taskgroup")
    private String realtimeTaskgroup;
    @Basic
    @Column(name = "need_sou")
    private String needSou;
    @Basic
    @Column(name = "need_sp")
    private String needSp;
    @Basic
    @Column(name = "sp_alltabs")
    private String spAlltabs;
    @Basic
    @Column(name = "sp_dest")
    private String spDest;
    @Basic
    @Column(name = "through_need_sou")
    private String throughNeedSou;
    @Basic
    @Column(name = "through_need_sp")
    private String throughNeedSp;
    @Basic
    @Column(name = "task_group")
    private String taskGroup;
    @Basic
    @Column(name = "param_sou")
    private String paramSou;
    @Basic
    @Column(name = "remark")
    private String remark;
    @Basic
    @Column(name = "spname")
    private String spname;
    @Basic
    @Column(name = "bvalid")
    private Integer bvalid;
    @Basic
    @Column(name = "brun")
    private Integer brun;
    @Basic
    @Column(name = "bfreq")
    private Integer bfreq;
    @Basic
    @Column(name = "bplan")
    private Integer bplan;

    @Formula("upper(sp_owner||'.'||sp_name||','||task_group||','||realtime_taskgroup)")
    private String spInfo;

}
