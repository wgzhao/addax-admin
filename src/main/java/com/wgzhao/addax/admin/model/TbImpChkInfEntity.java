package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "tb_imp_chk_inf", schema = "public", catalog = "stg01")
@Setter
@Getter
public class TbImpChkInfEntity {
    @Id
    @Basic
    @Column(name = "chk_idx")
    private String chkIdx;
    @Basic
    @Column(name = "chk_sendtype")
    private String chkSendtype;
    @Basic
    @Column(name = "chk_mobile")
    private String chkMobile;
    @Basic
    @Column(name = "bpntype")
    private Integer bpntype;
    @Basic
    @Column(name = "chk_kind")
    private String chkKind;
    @Basic
    @Column(name = "chk_sql")
    private String chkSql;
    @Basic
    @Column(name = "start_time")
    private Timestamp startTime;
    @Basic
    @Column(name = "end_time")
    private Timestamp endTime;
    @Basic
    @Column(name = "engine")
    private String engine;
}
