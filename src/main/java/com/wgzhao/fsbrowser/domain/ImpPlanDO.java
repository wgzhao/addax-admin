package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_PLAN实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpPlanDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 定时计划类型，参考参数1064
     */
    private String pnType;

    /**
     * 定点时间
     */
    private String pnFixed;

    /**
     * 轮询间隔
     */
    private Long pnInterval;

    /**
     * 轮询时间区间
     */
    private String pnRange;

    /**
     * PN_ID
     */
    private String pnId;

    /**
     * FLAG
     */
    private String flag;

    /**
     * START_TIME
     */
    private Date startTime;

    /**
     * END_TIME
     */
    private Date endTime;

    /**
     * 是否跳过切日时间段
     */
    private String bexit;

    /**
     * RUNTIME
     */
    private Long runtime;

    public String getPnType() {
        return this.pnType;
    }

    public void setPnType(String pnType) {
        this.pnType = pnType;
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

    public String getPnId() {
        return this.pnId;
    }

    public void setPnId(String pnId) {
        this.pnId = pnId;
    }

    public String getFlag() {
        return this.flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
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

    public String getBexit() {
        return this.bexit;
    }

    public void setBexit(String bexit) {
        this.bexit = bexit;
    }

    public Long getRuntime() {
        return this.runtime;
    }

    public void setRuntime(Long runtime) {
        this.runtime = runtime;
    }

}