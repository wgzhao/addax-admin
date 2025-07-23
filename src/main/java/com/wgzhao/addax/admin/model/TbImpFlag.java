package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "tb_imp_flag")
@Setter
@Getter
public class TbImpFlag {

    @Column(name = "tradedate")
    private Integer tradedate;

    @Column(name = "kind")
    private String kind;

    @Id
    @Column(name = "fid")
    private String fid;

    @Column(name = "fval")
    private String fval;

    @Column(name = "dw_clt_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date dwCltDate;
}
