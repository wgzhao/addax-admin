package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * TB_IMP_SP_NEEDTAB实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class ImpSpNeedtabDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * SP_ID
     */
    private String spId;

    /**
     * TABLE_NAME
     */
    private String tableName;

    /**
     * UPDT
     */
    private Date updt;

    /**
     * KIND
     */
    private String kind;

    public String getSpId() {
        return this.spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public String getTableName() {
        return this.tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public Date getUpdt() {
        return this.updt;
    }

    public void setUpdt(Date updt) {
        this.updt = updt;
    }

    public String getKind() {
        return this.kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

}