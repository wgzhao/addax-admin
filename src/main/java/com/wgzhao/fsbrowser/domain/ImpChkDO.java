package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_CHK实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpChkDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * CHK_KIND
     */
    private String chkKind;

    /**
     * CHK_NAME
     */
    private String chkName;

    /**
     * CHK_CONTENT
     */
    private String chkContent;

    /**
     * UPDT_DATE
     */
    private Date updtDate;

    /**
     * CHK_MOBILE
     */
    private String chkMobile;

    /**
     * CHK_SENDTYPE
     */
    private String chkSendtype;

    public String getChkKind() {
        return this.chkKind;
    }

    public void setChkKind(String chkKind) {
        this.chkKind = chkKind;
    }

    public String getChkName() {
        return this.chkName;
    }

    public void setChkName(String chkName) {
        this.chkName = chkName;
    }

    public String getChkContent() {
        return this.chkContent;
    }

    public void setChkContent(String chkContent) {
        this.chkContent = chkContent;
    }

    public Date getUpdtDate() {
        return this.updtDate;
    }

    public void setUpdtDate(Date updtDate) {
        this.updtDate = updtDate;
    }

    public String getChkMobile() {
        return this.chkMobile;
    }

    public void setChkMobile(String chkMobile) {
        this.chkMobile = chkMobile;
    }

    public String getChkSendtype() {
        return this.chkSendtype;
    }

    public void setChkSendtype(String chkSendtype) {
        this.chkSendtype = chkSendtype;
    }

}