package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;

/**
 * TMP_IMP_SP_NEEDTAB实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class TmpImpSpNeedtabDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SP_ID
     */
    private String spId;

    /**
     * SP_NAME
     */
    private String spName;

    /**
     * COM_TEXT
     */
    private String comText;

    public String getSpId() {
        return this.spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public String getSpName() {
        return this.spName;
    }

    public void setSpName(String spName) {
        this.spName = spName;
    }

    public String getComText() {
        return this.comText;
    }

    public void setComText(String comText) {
        this.comText = comText;
    }

}