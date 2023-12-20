package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;

/**
 * TB_IMP_SP_NEEDALL实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpSpNeedallDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SP_ID
     */
    private String spId;

    /**
     * 前置数据源
     */
    private String needSou;

    /**
     * 前置SP
     */
    private String needSp;

    /**
     * 所有前置表
     */
    private String spAlltabs;

    /**
     * SP生成表
     */
    private String spDest;

    /**
     * 穿透后所有前置数据源
     */
    private String throughNeedSou;

    /**
     * 穿透后所有前置SP
     */
    private String throughNeedSp;

    public String getSpId() {
        return this.spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public String getNeedSou() {
        return this.needSou;
    }

    public void setNeedSou(String needSou) {
        this.needSou = needSou;
    }

    public String getNeedSp() {
        return this.needSp;
    }

    public void setNeedSp(String needSp) {
        this.needSp = needSp;
    }

    public String getSpAlltabs() {
        return this.spAlltabs;
    }

    public void setSpAlltabs(String spAlltabs) {
        this.spAlltabs = spAlltabs;
    }

    public String getSpDest() {
        return this.spDest;
    }

    public void setSpDest(String spDest) {
        this.spDest = spDest;
    }

    public String getThroughNeedSou() {
        return this.throughNeedSou;
    }

    public void setThroughNeedSou(String throughNeedSou) {
        this.throughNeedSou = throughNeedSou;
    }

    public String getThroughNeedSp() {
        return this.throughNeedSp;
    }

    public void setThroughNeedSp(String throughNeedSp) {
        this.throughNeedSp = throughNeedSp;
    }

}