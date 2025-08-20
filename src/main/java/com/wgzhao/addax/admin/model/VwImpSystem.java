package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Entity
@Table(name = "vw_imp_system")
@Setter
@Getter
public class VwImpSystem {

    @Column(name = "sys_kind")
    private String sysKind;

    @Id
    @Column(name = "sysid")
    private String sysid;

    @Column(name = "sys_name")
    private String sysName;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "db_constr")
    private String dbConstr;

    @Column(name = "netchk")
    private String netchk;

    @Column(name = "bvalid")
    private BigInteger bvalid;

    @Column(name = "db_user")
    private String dbUser;

    @Column(name = "db_pass")
    private String dbPass;

    @Column(name = "db_paral")
    private BigInteger dbParal;

    @Column(name = "db_kind")
    private String dbKind;

    @Column(name = "db_kind_full")
    private String dbKindFull;

    @Column(name = "conf")
    private String conf;

    @Column(name = "start_kind")
    private String startKind;

    @Column(name = "db_start_type")
    private String dbStartType;

    @Column(name = "db_start")
    private String dbStart;

    @Column(name = "db_start_dt")
    private String dbStartDt;

    @Column(name = "create_db")
    private String createDb;

    @Column(name = "db_judge_sql")
    private String dbJudgeSql;

    @Column(name = "db_judge_pre")
    private String dbJudgePre;

    @Column(name = "db_conn")
    private String dbConn;

    @Column(name = "db_conn_cmd")
    private String dbConnCmd;

//    @Formula("sysname||sou_owner||sou_tablename||dest_owner||dest_tablename")
//    private String filterColumn;
}
