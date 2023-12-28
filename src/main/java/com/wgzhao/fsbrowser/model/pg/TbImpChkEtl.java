package com.wgzhao.fsbrowser.model.pg;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "tb_imp_chk_etl")
@Setter
@Getter
public class TbImpChkEtl {

    @Id
    @Column(name = "tblname")
    private String tblname;

    @Column(name = "kind")
    private String kind;

    @Column(name = "cnt")
    private Integer cnt;

    @Column(name = "updt_date")
    private Timestamp updtDate;

    @Column(name = "logdate")
    private String logdate;
}
