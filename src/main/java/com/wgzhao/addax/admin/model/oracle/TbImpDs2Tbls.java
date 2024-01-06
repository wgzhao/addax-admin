package com.wgzhao.addax.admin.model.oracle;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "TB_IMP_DS2_TBLS", schema = "STG01", catalog = "")
@Setter
@Getter
public class TbImpDs2Tbls {
    @Basic
    @Column(name = "DS_ID")
    private String dsId;
    @Basic
    @Column(name = "SOU_ISHDP")
    private String souIshdp;
    @Basic
    @Column(name = "SOU_TABLE")
    private String souTable;
    @Basic
    @Column(name = "SOU_FILTER")
    private String souFilter;
    @Basic
    @Column(name = "DEST_OWNER")
    private String destOwner;
    @Basic
    @Column(name = "DEST_TABLENAME")
    private String destTablename;
    @Basic
    @Column(name = "FLAG")
    private String flag;
    @Basic
    @Column(name = "START_TIME")
    private Date startTime;
    @Basic
    @Column(name = "END_TIME")
    private Date endTime;
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "TBL_ID")
    private String tblId;
    @Basic
    @Column(name = "PRE_SQL")
    private String preSql;
    @Basic
    @Column(name = "COL_MAP")
    private String colMap;
    @Basic
    @Column(name = "POST_SQL")
    private String postSql;
    @Basic
    @Column(name = "MAX_RUNTIME")
    private BigInteger maxRuntime;
}
