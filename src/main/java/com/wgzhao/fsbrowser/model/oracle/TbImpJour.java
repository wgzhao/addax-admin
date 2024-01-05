package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "TB_IMP_JOUR", schema = "STG01", catalog = "")
@Setter
@Getter
public class TbImpJour {
    @Basic
    @Column(name = "KIND")
    private String kind;
    @Basic
    @Column(name = "TRADE_DATE")
    private Integer tradeDate;
    @Basic
    @Column(name = "STATUS")
    private String status;

    @Id
    @Basic
    @Column(name = "KEY_ID")
    private String keyId;
    @Basic
    @Column(name = "REMARK")
    private String remark;
    @Basic
    @Column(name = "UPDT_DATE")
    private Date updtDate;
}
