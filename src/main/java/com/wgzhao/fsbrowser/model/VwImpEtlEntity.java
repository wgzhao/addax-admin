package com.wgzhao.fsbrowser.model;

import jakarta.persistence.*;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "VW_IMP_ETL", schema = "STG01", catalog = "")
public class VwImpEtlEntity {
    @Basic
    @Column(name = "WKF", nullable = true, length = 42)
    private String wkf;

    @Id
    @Basic
    @Column(name = "SYSID", nullable = true, length = 64)
    private String sysid;
    @Basic
    @Column(name = "SYS_NAME", nullable = true, length = 210)
    private String sysName;
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
    @Column(name = "SOU_DB_CONN", nullable = true, length = 35)
    private String souDbConn;
    @Basic
    @Column(name = "SOU_DB_KIND", nullable = true, length = 2000)
    private String souDbKind;
    @Basic
    @Column(name = "SOU_DB_CONSTR", nullable = true, length = 500)
    private String souDbConstr;
    @Basic
    @Column(name = "SOU_DB_USER", nullable = true, length = 64)
    private String souDbUser;
    @Basic
    @Column(name = "SOU_DB_PASS", nullable = true, length = 64)
    private String souDbPass;
    @Basic
    @Column(name = "SOU_DB_CONF", nullable = true, length = 4000)
    private String souDbConf;
    @Basic
    @Column(name = "SOU_OWNER", nullable = true, length = 32)
    private String souOwner;
    @Basic
    @Column(name = "SOU_TABLENAME", nullable = true, length = 4000)
    private String souTablename;
    @Basic
    @Column(name = "SOU_FILTER", nullable = true, length = 2000)
    private String souFilter;
    @Basic
    @Column(name = "SOU_SPLIT", nullable = true, length = 32)
    private String souSplit;
    @Basic
    @Column(name = "DEST_OWNER", nullable = true, length = 35)
    private String destOwner;
    @Basic
    @Column(name = "DEST_TABLENAME", nullable = false, length = 64)
    private String destTablename;
    @Basic
    @Column(name = "DEST_PART_KIND", nullable = true, length = 1)
    private String destPartKind;
    @Basic
    @Column(name = "FLAG", nullable = true, length = 1)
    private String flag;
    @Basic
    @Column(name = "PARAM_SOU", nullable = true, length = 1)
    private String paramSou;
    @Basic
    @Column(name = "BUPDATE", nullable = true, length = 1)
    private String bupdate;
    @Basic
    @Column(name = "BCREATE", nullable = true, length = 1)
    private String bcreate;
    @Basic
    @Column(name = "ETL_KIND", nullable = true, length = 1)
    private String etlKind;
    @Basic
    @Column(name = "RETRY_CNT", nullable = true, precision = 0)
    private Boolean retryCnt;
    @Basic
    @Column(name = "START_TIME", nullable = true)
    private Date startTime;
    @Basic
    @Column(name = "END_TIME", nullable = true)
    private Date endTime;
    @Basic
    @Column(name = "RUNTIME", nullable = true, precision = 0)
    private Integer runtime;
    @Basic
    @Column(name = "RUNTIME_ADD", nullable = true, precision = 0)
    private Integer runtimeAdd;
    @Basic
    @Column(name = "TID", nullable = false, length = 32)
    private String tid;
    @Basic
    @Column(name = "RID", nullable = true)
    private Object rid;
    @Basic
    @Column(name = "SPNAME", nullable = true, length = 107)
    private String spname;
    @Basic
    @Column(name = "DEST", nullable = true, length = 100)
    private String dest;
    @Basic
    @Column(name = "BPREVIEW", nullable = true, length = 1)
    private String bpreview;
    @Basic
    @Column(name = "BTDH", nullable = true, length = 1)
    private String btdh;
    @Basic
    @Column(name = "BREALTIME", nullable = true, precision = 0)
    private BigInteger brealtime;
    @Basic
    @Column(name = "REALTIME_INTERVAL", nullable = true, precision = 0)
    private Short realtimeInterval;
    @Basic
    @Column(name = "REALTIME_INTERVAL_RANGE", nullable = true, length = 100)
    private String realtimeIntervalRange;
    @Basic
    @Column(name = "REALTIME_TASKGROUP", nullable = true, length = 32)
    private String realtimeTaskgroup;
    @Basic
    @Column(name = "REALTIME_FIXED", nullable = true, length = 32)
    private String realtimeFixed;
    @Basic
    @Column(name = "BAFTER_RETRY", nullable = true, precision = 0)
    private BigInteger bafterRetry;
    @Basic
    @Column(name = "AFTER_RETRY_FIXED", nullable = true, length = 32)
    private String afterRetryFixed;
    @Basic
    @Column(name = "AFTER_RETRY_PNTYPE", nullable = true, length = 1)
    private String afterRetryPntype;
    @Basic
    @Column(name = "BRUN", nullable = true, precision = 0)
    private BigInteger brun;
    @Basic
    @Column(name = "BVALID", nullable = true, precision = 0)
    private BigInteger bvalid;
    @Basic
    @Column(name = "BCJ", nullable = true, precision = 0)
    private BigInteger bcj;
    @Basic
    @Column(name = "JOBKIND", nullable = true, length = 6)
    private String jobkind;

    public String getWkf() {
        return wkf;
    }

    public void setWkf(String wkf) {
        this.wkf = wkf;
    }

    public String getSysid() {
        return sysid;
    }

    public void setSysid(String sysid) {
        this.sysid = sysid;
    }

    public String getSysName() {
        return sysName;
    }

    public void setSysName(String sysName) {
        this.sysName = sysName;
    }

    public String getSysname() {
        return sysname;
    }

    public void setSysname(String sysname) {
        this.sysname = sysname;
    }

    public String getDbStart() {
        return dbStart;
    }

    public void setDbStart(String dbStart) {
        this.dbStart = dbStart;
    }

    public String getDbStartDt() {
        return dbStartDt;
    }

    public void setDbStartDt(String dbStartDt) {
        this.dbStartDt = dbStartDt;
    }

    public String getSouDbConn() {
        return souDbConn;
    }

    public void setSouDbConn(String souDbConn) {
        this.souDbConn = souDbConn;
    }

    public String getSouDbKind() {
        return souDbKind;
    }

    public void setSouDbKind(String souDbKind) {
        this.souDbKind = souDbKind;
    }

    public String getSouDbConstr() {
        return souDbConstr;
    }

    public void setSouDbConstr(String souDbConstr) {
        this.souDbConstr = souDbConstr;
    }

    public String getSouDbUser() {
        return souDbUser;
    }

    public void setSouDbUser(String souDbUser) {
        this.souDbUser = souDbUser;
    }

    public String getSouDbPass() {
        return souDbPass;
    }

    public void setSouDbPass(String souDbPass) {
        this.souDbPass = souDbPass;
    }

    public String getSouDbConf() {
        return souDbConf;
    }

    public void setSouDbConf(String souDbConf) {
        this.souDbConf = souDbConf;
    }

    public String getSouOwner() {
        return souOwner;
    }

    public void setSouOwner(String souOwner) {
        this.souOwner = souOwner;
    }

    public String getSouTablename() {
        return souTablename;
    }

    public void setSouTablename(String souTablename) {
        this.souTablename = souTablename;
    }

    public String getSouFilter() {
        return souFilter;
    }

    public void setSouFilter(String souFilter) {
        this.souFilter = souFilter;
    }

    public String getSouSplit() {
        return souSplit;
    }

    public void setSouSplit(String souSplit) {
        this.souSplit = souSplit;
    }

    public String getDestOwner() {
        return destOwner;
    }

    public void setDestOwner(String destOwner) {
        this.destOwner = destOwner;
    }

    public String getDestTablename() {
        return destTablename;
    }

    public void setDestTablename(String destTablename) {
        this.destTablename = destTablename;
    }

    public String getDestPartKind() {
        return destPartKind;
    }

    public void setDestPartKind(String destPartKind) {
        this.destPartKind = destPartKind;
    }

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getParamSou() {
        return paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public String getBupdate() {
        return bupdate;
    }

    public void setBupdate(String bupdate) {
        this.bupdate = bupdate;
    }

    public String getBcreate() {
        return bcreate;
    }

    public void setBcreate(String bcreate) {
        this.bcreate = bcreate;
    }

    public String getEtlKind() {
        return etlKind;
    }

    public void setEtlKind(String etlKind) {
        this.etlKind = etlKind;
    }

    public Boolean getRetryCnt() {
        return retryCnt;
    }

    public void setRetryCnt(Boolean retryCnt) {
        this.retryCnt = retryCnt;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public Integer getRuntimeAdd() {
        return runtimeAdd;
    }

    public void setRuntimeAdd(Integer runtimeAdd) {
        this.runtimeAdd = runtimeAdd;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public Object getRid() {
        return rid;
    }

    public void setRid(Object rid) {
        this.rid = rid;
    }

    public String getSpname() {
        return spname;
    }

    public void setSpname(String spname) {
        this.spname = spname;
    }

    public String getDest() {
        return dest;
    }

    public void setDest(String dest) {
        this.dest = dest;
    }

    public String getBpreview() {
        return bpreview;
    }

    public void setBpreview(String bpreview) {
        this.bpreview = bpreview;
    }

    public String getBtdh() {
        return btdh;
    }

    public void setBtdh(String btdh) {
        this.btdh = btdh;
    }

    public BigInteger getBrealtime() {
        return brealtime;
    }

    public void setBrealtime(BigInteger brealtime) {
        this.brealtime = brealtime;
    }

    public Short getRealtimeInterval() {
        return realtimeInterval;
    }

    public void setRealtimeInterval(Short realtimeInterval) {
        this.realtimeInterval = realtimeInterval;
    }

    public String getRealtimeIntervalRange() {
        return realtimeIntervalRange;
    }

    public void setRealtimeIntervalRange(String realtimeIntervalRange) {
        this.realtimeIntervalRange = realtimeIntervalRange;
    }

    public String getRealtimeTaskgroup() {
        return realtimeTaskgroup;
    }

    public void setRealtimeTaskgroup(String realtimeTaskgroup) {
        this.realtimeTaskgroup = realtimeTaskgroup;
    }

    public String getRealtimeFixed() {
        return realtimeFixed;
    }

    public void setRealtimeFixed(String realtimeFixed) {
        this.realtimeFixed = realtimeFixed;
    }

    public BigInteger getBafterRetry() {
        return bafterRetry;
    }

    public void setBafterRetry(BigInteger bafterRetry) {
        this.bafterRetry = bafterRetry;
    }

    public String getAfterRetryFixed() {
        return afterRetryFixed;
    }

    public void setAfterRetryFixed(String afterRetryFixed) {
        this.afterRetryFixed = afterRetryFixed;
    }

    public String getAfterRetryPntype() {
        return afterRetryPntype;
    }

    public void setAfterRetryPntype(String afterRetryPntype) {
        this.afterRetryPntype = afterRetryPntype;
    }

    public BigInteger getBrun() {
        return brun;
    }

    public void setBrun(BigInteger brun) {
        this.brun = brun;
    }

    public BigInteger getBvalid() {
        return bvalid;
    }

    public void setBvalid(BigInteger bvalid) {
        this.bvalid = bvalid;
    }

    public BigInteger getBcj() {
        return bcj;
    }

    public void setBcj(BigInteger bcj) {
        this.bcj = bcj;
    }

    public String getJobkind() {
        return jobkind;
    }

    public void setJobkind(String jobkind) {
        this.jobkind = jobkind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpEtlEntity that = (VwImpEtlEntity) o;
        return Objects.equals(wkf, that.wkf) && Objects.equals(sysid, that.sysid) && Objects.equals(sysName, that.sysName) && Objects.equals(sysname, that.sysname) && Objects.equals(dbStart, that.dbStart) && Objects.equals(dbStartDt, that.dbStartDt) && Objects.equals(souDbConn, that.souDbConn) && Objects.equals(souDbKind, that.souDbKind) && Objects.equals(souDbConstr, that.souDbConstr) && Objects.equals(souDbUser, that.souDbUser) && Objects.equals(souDbPass, that.souDbPass) && Objects.equals(souDbConf, that.souDbConf) && Objects.equals(souOwner, that.souOwner) && Objects.equals(souTablename, that.souTablename) && Objects.equals(souFilter, that.souFilter) && Objects.equals(souSplit, that.souSplit) && Objects.equals(destOwner, that.destOwner) && Objects.equals(destTablename, that.destTablename) && Objects.equals(destPartKind, that.destPartKind) && Objects.equals(flag, that.flag) && Objects.equals(paramSou, that.paramSou) && Objects.equals(bupdate, that.bupdate) && Objects.equals(bcreate, that.bcreate) && Objects.equals(etlKind, that.etlKind) && Objects.equals(retryCnt, that.retryCnt) && Objects.equals(startTime, that.startTime) && Objects.equals(endTime, that.endTime) && Objects.equals(runtime, that.runtime) && Objects.equals(runtimeAdd, that.runtimeAdd) && Objects.equals(tid, that.tid) && Objects.equals(rid, that.rid) && Objects.equals(spname, that.spname) && Objects.equals(dest, that.dest) && Objects.equals(bpreview, that.bpreview) && Objects.equals(btdh, that.btdh) && Objects.equals(brealtime, that.brealtime) && Objects.equals(realtimeInterval, that.realtimeInterval) && Objects.equals(realtimeIntervalRange, that.realtimeIntervalRange) && Objects.equals(realtimeTaskgroup, that.realtimeTaskgroup) && Objects.equals(realtimeFixed, that.realtimeFixed) && Objects.equals(bafterRetry, that.bafterRetry) && Objects.equals(afterRetryFixed, that.afterRetryFixed) && Objects.equals(afterRetryPntype, that.afterRetryPntype) && Objects.equals(brun, that.brun) && Objects.equals(bvalid, that.bvalid) && Objects.equals(bcj, that.bcj) && Objects.equals(jobkind, that.jobkind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wkf, sysid, sysName, sysname, dbStart, dbStartDt, souDbConn, souDbKind, souDbConstr, souDbUser, souDbPass, souDbConf, souOwner, souTablename, souFilter, souSplit, destOwner, destTablename, destPartKind, flag, paramSou, bupdate, bcreate, etlKind, retryCnt, startTime, endTime, runtime, runtimeAdd, tid, rid, spname, dest, bpreview, btdh, brealtime, realtimeInterval, realtimeIntervalRange, realtimeTaskgroup, realtimeFixed, bafterRetry, afterRetryFixed, afterRetryPntype, brun, bvalid, bcj, jobkind);
    }
}
