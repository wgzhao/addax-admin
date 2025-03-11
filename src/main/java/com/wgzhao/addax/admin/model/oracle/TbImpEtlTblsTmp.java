package com.wgzhao.addax.admin.model.oracle;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "TB_IMP_ETL_TBLS_TMP")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class TbImpEtlTblsTmp
{
    private int dbId;
    private String dbName;
    private String dbLocation;
    private int tblId;
    private String tblName;
    private String tblType;
    private String tblLocation;
    private int cdId;
    private String tblComment;
    private String colName;
    private String colType;
    private String colComment;
    private int colIdx;
}
