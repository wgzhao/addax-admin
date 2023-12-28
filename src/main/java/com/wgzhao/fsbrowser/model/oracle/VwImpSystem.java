package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.math.BigInteger;

@Entity
@Table(name = "VW_IMP_SYSTEM")
@Setter
@Getter
public class VwImpSystem {

    @Column(name = "SYS_KIND")
    private String sysKind;

    @Id
    @Column(name = "SYSID")
    private String sysid;

    @Column(name = "SYS_NAME")
    private String sysName;

    @Column(name = "DB_NAME")
    private String dbName;

    @Column(name = "DB_CONSTR")
    private String dbConstr;

    @Column(name = "NETCHK")
    private String netchk;

    @Column(name = "BVALID")
    private BigInteger bvalid;

    @Column(name = "DB_USER")
    private String dbUser;

    @Column(name = "DB_PASS")
    private String dbPass;

    @Column(name = "DB_PARAL")
    private BigInteger dbParal;

    @Column(name = "DB_KIND")
    private String dbKind;

    @Column(name = "DB_KIND_FULL")
    private String dbKindFull;

    @Column(name = "CONF")
    private String conf;

    @Column(name = "START_KIND")
    private String startKind;

    @Column(name = "DB_START_TYPE")
    private String dbStartType;

    @Column(name = "DB_START")
    private String dbStart;

    @Column(name = "DB_START_DT")
    private String dbStartDt;

    @Column(name = "CREATE_DB")
    private String createDb;

    @Column(name = "DB_JUDGE_SQL")
    private String dbJudgeSql;

    @Column(name = "DB_JUDGE_PRE")
    private String dbJudgePre;

    @Column(name = "DB_CONN")
    private String dbConn;

    @Column(name = "DB_CONN_CMD")
    private String dbConnCmd;

//    @Formula("sysname||sou_owner||sou_tablename||dest_owner||dest_tablename")
//    private String filterColumn;
}
