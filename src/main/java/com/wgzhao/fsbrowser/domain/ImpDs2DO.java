package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_DS2实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpDs2DO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * TASK_GROUP
     */
    private String taskGroup;

    /**
     * DEST_SYSID
     */
    private String destSysid;

    /**
     * DEST_OWNER
     */
    private String destOwner;

    /**
     * FLAG
     */
    private String flag;

    /**
     * PARAM_SOU
     */
    private String paramSou;

    /**
     * RETRY_CNT
     */
    private Long retryCnt;

    /**
     * START_TIME
     */
    private Date startTime;

    /**
     * END_TIME
     */
    private Date endTime;

    /**
     * DS_ID
     */
    private String dsId;

    /**
     * PRE_SQL
     */
    private String preSql;

    /**
     * POST_SQL
     */
    private String postSql;

    /**
     * COL_MAP
     */
    private String colMap;

    /**
     * 执行并发数
     */
    private Long paralNum;

    /**
     * BUPDATE
     */
    private String bupdate;

    /**
     * RUNTIME
     */
    private Long runtime;

    /**
     * PRE_SH
     */
    private String preSh;

    /**
     * POST_SH
     */
    private String postSh;

    /**
     * 允许最长耗时
     */
    private Long maxRuntime;

    /**
     * 运行频率，周期(D天,W周,M月,Q季度,Y年度)
     */
    private String runFreq;

    /**
     * PN_FIXED
     */
    private String pnFixed;

    /**
     * PN_INTERVAL
     */
    private Long pnInterval;

    /**
     * PN_RANGE
     */
    private String pnRange;

    /**
     * PN_TYPE
     */
    private String pnType;

    /**
     * REMARK
     */
    private String remark;

    public String getTaskGroup() {
        return this.taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getDestSysid() {
        return this.destSysid;
    }

    public void setDestSysid(String destSysid) {
        this.destSysid = destSysid;
    }

    public String getDestOwner() {
        return this.destOwner;
    }

    public void setDestOwner(String destOwner) {
        this.destOwner = destOwner;
    }

    public String getFlag() {
        return this.flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public String getParamSou() {
        return this.paramSou;
    }

    public void setParamSou(String paramSou) {
        this.paramSou = paramSou;
    }

    public Long getRetryCnt() {
        return this.retryCnt;
    }

    public void setRetryCnt(Long retryCnt) {
        this.retryCnt = retryCnt;
    }

    public Date getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return this.endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getDsId() {
        return this.dsId;
    }

    public void setDsId(String dsId) {
        this.dsId = dsId;
    }

    public String getPreSql() {
        return this.preSql;
    }

    public void setPreSql(String preSql) {
        this.preSql = preSql;
    }

    public String getPostSql() {
        return this.postSql;
    }

    public void setPostSql(String postSql) {
        this.postSql = postSql;
    }

    public String getColMap() {
        return this.colMap;
    }

    public void setColMap(String colMap) {
        this.colMap = colMap;
    }

    public Long getParalNum() {
        return this.paralNum;
    }

    public void setParalNum(Long paralNum) {
        this.paralNum = paralNum;
    }

    public String getBupdate() {
        return this.bupdate;
    }

    public void setBupdate(String bupdate) {
        this.bupdate = bupdate;
    }

    public Long getRuntime() {
        return this.runtime;
    }

    public void setRuntime(Long runtime) {
        this.runtime = runtime;
    }

    public String getPreSh() {
        return this.preSh;
    }

    public void setPreSh(String preSh) {
        this.preSh = preSh;
    }

    public String getPostSh() {
        return this.postSh;
    }

    public void setPostSh(String postSh) {
        this.postSh = postSh;
    }

    public Long getMaxRuntime() {
        return this.maxRuntime;
    }

    public void setMaxRuntime(Long maxRuntime) {
        this.maxRuntime = maxRuntime;
    }

    public String getRunFreq() {
        return this.runFreq;
    }

    public void setRunFreq(String runFreq) {
        this.runFreq = runFreq;
    }

    public String getPnFixed() {
        return this.pnFixed;
    }

    public void setPnFixed(String pnFixed) {
        this.pnFixed = pnFixed;
    }

    public Long getPnInterval() {
        return this.pnInterval;
    }

    public void setPnInterval(Long pnInterval) {
        this.pnInterval = pnInterval;
    }

    public String getPnRange() {
        return this.pnRange;
    }

    public void setPnRange(String pnRange) {
        this.pnRange = pnRange;
    }

    public String getPnType() {
        return this.pnType;
    }

    public void setPnType(String pnType) {
        this.pnType = pnType;
    }

    public String getRemark() {
        return this.remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}