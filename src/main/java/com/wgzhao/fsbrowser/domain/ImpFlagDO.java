package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_FLAG实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpFlagDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * TRADEDATE
     */
    private Long tradedate;

    /**
     * KIND
     */
    private String kind;

    /**
     * FID
     */
    private String fid;

    /**
     * FVAL
     */
    private String fval;

    /**
     * DW_CLT_DATE
     */
    private Date dwCltDate;

    public Long getTradedate() {
        return this.tradedate;
    }

    public void setTradedate(Long tradedate) {
        this.tradedate = tradedate;
    }

    public String getKind() {
        return this.kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFid() {
        return this.fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getFval() {
        return this.fval;
    }

    public void setFval(String fval) {
        this.fval = fval;
    }

    public Date getDwCltDate() {
        return this.dwCltDate;
    }

    public void setDwCltDate(Date dwCltDate) {
        this.dwCltDate = dwCltDate;
    }

}