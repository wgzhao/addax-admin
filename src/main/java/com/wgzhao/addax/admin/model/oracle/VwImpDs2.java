package com.wgzhao.addax.admin.model.oracle;

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
@Table(name = "VW_IMP_DS2", schema = "STG01", catalog = "")
@Setter
@Getter
public class VwImpDs2 {

    @Id
    @Basic
    @Column(name = "DS_ID")
    private String dsId;
    @Basic
    @Column(name = "DS_NAME")
    private String dsName;
    @Basic
    @Column(name = "DEST_SYSID")
    private String destSysid;
    @Basic
    @Column(name = "TASK_GROUP")
    private String taskGroup;
    @Basic
    @Column(name = "PARAM_SOU")
    private String paramSou;
    @Basic
    @Column(name = "RETRY_CNT")
    private Integer retryCnt;
    @Basic
    @Column(name = "RUN_FREQ")
    private String runFreq;
    @Basic
    @Column(name = "BVALID")
    private Integer bvalid;
    @Basic
    @Column(name = "FLAG")
    private String flag;
    @Basic
    @Column(name = "START_TIME")
    private Date startTime;
    @Basic
    @Column(name = "END_TIME")
    private Date endTime;
    @Basic
    @Column(name = "RUNTIME")
    private Integer runtime;
    @Basic
    @Column(name = "BRUN")
    private Integer brun;
    @Basic
    @Column(name = "BDELAY")
    private Integer bdelay;
    @Basic
    @Column(name = "BFREQ")
    private Integer bfreq;
    @Basic
    @Column(name = "BPLAN")
    private Integer bplan;
    @Basic
    @Column(name = "PRE_SH")
    private String preSh;
    @Basic
    @Column(name = "POST_SH")
    private String postSh;
    @Basic
    @Column(name = "PRE_SQL")
    private String preSql;
    @Basic
    @Column(name = "POST_SQL")
    private String postSql;
    @Basic
    @Column(name = "INIT_RDS")
    private String initRds;

    @Formula("fn_imp_value('ds_needs',ds_id)")
    private String needs;
}
