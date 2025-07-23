package com.wgzhao.addax.admin.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.math.BigInteger;
import java.sql.Date;

@Entity
@Table(name = "tb_imp_ds2_tbls")
@Setter
@Getter
public class TbImpDs2Tbls {
    @Basic
    @Column(name = "ds_id")
    private String dsId;
    @Basic
    @Column(name = "sou_ishdp")
    private String souIshdp;
    @Basic
    @Column(name = "sou_table")
    private String souTable;
    @Basic
    @Column(name = "sou_filter")
    private String souFilter;
    @Basic
    @Column(name = "dest_owner")
    private String destOwner;
    @Basic
    @Column(name = "dest_tablename")
    private String destTablename;
    @Basic
    @Column(name = "flag")
    private String flag;
    @Basic
    @Column(name = "start_time")
    private Date startTime;
    @Basic
    @Column(name = "end_time")
    private Date endTime;
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "tbl_id")
    private String tblId;
    @Basic
    @Column(name = "pre_sql")
    private String preSql;
    @Basic
    @Column(name = "col_map")
    private String colMap;
    @Basic
    @Column(name = "post_sql")
    private String postSql;
    @Basic
    @Column(name = "max_runtime")
    private BigInteger maxRuntime;

    // 采集配置文件
    @Formula("to_char(fn_imp_value('ds_json', TBL_ID))")
    private String dsJson;
}
