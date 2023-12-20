package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * HADOOP_SP的运行脚本（作为主表的附属表）实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpSpComDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主表的SP_ID
     */
    private String spId;

    /**
     * 命令执行顺序
     */
    private Long comIdx;

    /**
     * 命令类型：hive、presto、spark-sql、shell、spark，参照runcmds.sh的runkind
     */
    private String comKind;

    /**
     * 命令正文
     */
    private String comText;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * COM_ID
     */
    private String comId;

    /**
     * 状态(包含生成脚本状态、执行情况)
     */
    private String flag;

    public String getSpId() {
        return this.spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public Long getComIdx() {
        return this.comIdx;
    }

    public void setComIdx(Long comIdx) {
        this.comIdx = comIdx;
    }

    public String getComKind() {
        return this.comKind;
    }

    public void setComKind(String comKind) {
        this.comKind = comKind;
    }

    public String getComText() {
        return this.comText;
    }

    public void setComText(String comText) {
        this.comText = comText;
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

    public String getComId() {
        return this.comId;
    }

    public void setComId(String comId) {
        this.comId = comId;
    }

    public String getFlag() {
        return this.flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

}