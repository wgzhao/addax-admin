package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SP", schema = "STG01", catalog = "")
@Setter
@Getter
public class VwImpSp {
    @Basic
    @Column(name = "SP_OWNER")
    private String spOwner;
    @Basic
    @Column(name = "SP_NAME")
    private String spName;
    @Basic
    @Column(name = "SP_ID")
    private String spId;

    @Id
    @Basic
    @Column(name = "RID")
    private String rid;
    @Basic
    @Column(name = "FLAG")
    private String flag;
    @Basic
    @Column(name = "RUN_FREQ")
    private String runFreq;
    @Basic
    @Column(name = "START_TIME")
    private Date startTime;
    @Basic
    @Column(name = "END_TIME")
    private Date endTime;
    @Basic
    @Column(name = "RETRY_CNT")
    private Byte retryCnt;
    @Basic
    @Column(name = "RUNTIME")
    private Integer runtime;
    @Basic
    @Column(name = "REALTIME_TASKGROUP")
    private String realtimeTaskgroup;
    @Basic
    @Column(name = "NEED_SOU")
    private String needSou;
    @Basic
    @Column(name = "NEED_SP")
    private String needSp;
    @Basic
    @Column(name = "SP_ALLTABS")
    private String spAlltabs;
    @Basic
    @Column(name = "SP_DEST")
    private String spDest;
    @Basic
    @Column(name = "THROUGH_NEED_SOU")
    private String throughNeedSou;
    @Basic
    @Column(name = "THROUGH_NEED_SP")
    private String throughNeedSp;
    @Basic
    @Column(name = "TASK_GROUP")
    private String taskGroup;
    @Basic
    @Column(name = "PARAM_SOU")
    private String paramSou;
    @Basic
    @Column(name = "REMARK")
    private String remark;
    @Basic
    @Column(name = "SPNAME")
    private String spname;
    @Basic
    @Column(name = "BVALID")
    private Integer bvalid;
    @Basic
    @Column(name = "BRUN")
    private Integer brun;
    @Basic
    @Column(name = "BFREQ")
    private Integer bfreq;
    @Basic
    @Column(name = "BPLAN")
    private Integer bplan;

    @Formula("upper(sp_owner||'.'||sp_name||','||task_group||','||realtime_taskgroup)")
    private String spInfo;

}
