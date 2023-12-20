package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_JOUR实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpJourDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * KIND
     */
    private String kind;

    /**
     * TRADE_DATE
     */
    private Long tradeDate;

    /**
     * STATUS
     */
    private String status;

    /**
     * KEY_ID
     */
    private String keyId;

    /**
     * REMARK
     */
    private String remark;

    /**
     * UPDT_DATE
     */
    private Date updtDate;

    public String getKind() {
        return this.kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getTradeDate() {
        return this.tradeDate;
    }

    public void setTradeDate(Long tradeDate) {
        this.tradeDate = tradeDate;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKeyId() {
        return this.keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getRemark() {
        return this.remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getUpdtDate() {
        return this.updtDate;
    }

    public void setUpdtDate(Date updtDate) {
        this.updtDate = updtDate;
    }

}