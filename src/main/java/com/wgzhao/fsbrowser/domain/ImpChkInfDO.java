package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_CHK_INF实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpChkInfDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * CHK_IDX
     */
    private String chkIdx;

    /**
     * CHK_SENDTYPE
     */
    private String chkSendtype;

    /**
     * CHK_MOBILE
     */
    private String chkMobile;

    /**
     * BPNTYPE
     */
    private Long bpntype;

    /**
     * CHK_KIND
     */
    private String chkKind;

    /**
     * CHK_SQL
     */
    private String chkSql;

    /**
     * START_TIME
     */
    private Date startTime;

    /**
     * END_TIME
     */
    private Date endTime;

    /**
     * ENGINE
     */
    private String engine;

    public String getChkIdx() {
        return this.chkIdx;
    }

    public void setChkIdx(String chkIdx) {
        this.chkIdx = chkIdx;
    }

    public String getChkSendtype() {
        return this.chkSendtype;
    }

    public void setChkSendtype(String chkSendtype) {
        this.chkSendtype = chkSendtype;
    }

    public String getChkMobile() {
        return this.chkMobile;
    }

    public void setChkMobile(String chkMobile) {
        this.chkMobile = chkMobile;
    }

    public Long getBpntype() {
        return this.bpntype;
    }

    public void setBpntype(Long bpntype) {
        this.bpntype = bpntype;
    }

    public String getChkKind() {
        return this.chkKind;
    }

    public void setChkKind(String chkKind) {
        this.chkKind = chkKind;
    }

    public String getChkSql() {
        return this.chkSql;
    }

    public void setChkSql(String chkSql) {
        this.chkSql = chkSql;
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

    public String getEngine() {
        return this.engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

}