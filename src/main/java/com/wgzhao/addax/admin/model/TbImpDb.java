package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="tb_imp_db")
@Setter
@Getter
@Data
public class TbImpDb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "did", length = 32, updatable = false, nullable = false, insertable = false)
    private String id;

    @Column(name = "db_name")
    private String dbName;

    @Column(name = "db_constr")
    private String dbConstr;

    @Column(name = "db_id_etl")
    private String dbIdEtl;

    @Column(name = "db_user_etl")
    private String dbUserEtl;

    @Column(name = "db_pass_etl")
    private String dbPassEtl;

    @Column(name = "db_paral_etl")
    private Integer dbParalEtl;

    @Column(name = "db_id_ds")
    private String DbIdDs;

    @Column(name = "db_user_ds")
    private String dbUserDs;

    @Column(name = "db_pass_ds")
    private String dbPassDs;

    @Column(name = "db_paral_ds")
    private Integer dbParalDs;

    @Column(name = "db_start")
    private String dbStart;

    @Column(name = "db_start_type")
    private String dbStartType;

    @Column(name = "db_judge_sql")
    private String dbJudgeSql;

    @Column(name = "db_judge_pre")
    private String  dbJudgePre;

    @Column(name = "db_remark")
    private String dbRemark;

    @Column(name = "bvalid")
    private String bvalid;

    @Column(name = "conf")
    private String conf;
}
