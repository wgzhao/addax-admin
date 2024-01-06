package com.wgzhao.addax.admin.model.pg;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "tb_imp_chk_sp")
@Setter
@Getter
public class TbImpChkSpEntity {

    @Id
    @Basic
    @Column(name = "proc_name")
    private String procName;
    @Basic
    @Column(name = "check_item")
    private String checkItem;
    @Basic
    @Column(name = "check_sou")
    private String checkSou;
    @Basic
    @Column(name = "check_value")
    private BigDecimal checkValue;
    @Basic
    @Column(name = "remark")
    private String remark;
    @Basic
    @Column(name = "updt_date")
    private Timestamp updtDate;
    @Basic
    @Column(name = "logdate")
    private String logdate;
}
