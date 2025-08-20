package com.wgzhao.addax.admin.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "tb_imp_jour")
@Setter
@Getter
public class TbImpJour {
    @Basic
    @Column(name = "kind")
    private String kind;
    @Basic
    @Column(name = "trade_date")
    private Integer tradeDate;
    @Basic
    @Column(name = "status")
    private String status;

    @Id
    @Basic
    @Column(name = "key_id")
    private String keyId;
    @Basic
    @Column(name = "remark")
    private String remark;
    @Basic
    @Column(name = "updt_date")
    private Date updtDate;
}
