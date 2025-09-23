package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "tb_imp_tbl_hdp")
@IdClass(TbImpTblHdp.PK.class)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TbImpTblHdp {
    @Id
    @Column(name = "tid", length = 32, nullable = false)
    private String tid;

    @Column(name = "hive_owner", length = 35)
    private String hiveOwner;

    @Column(name = "hive_tablename", length = 64, nullable = false)
    private String hiveTablename;

    @Id
    @Column(name = "col_name", length = 255)
    private String colName;

    @Column(name = "col_type_full", length = 500)
    private String colTypeFull;

    @Column(name = "col_type", length = 2000)
    private String colType;

    @Column(name = "col_precision")
    private Integer colPrecision;

    @Column(name = "col_scale")
    private Integer colScale;

    @Column(name = "col_idx")
    private Integer colIdx;

    @Column(name = "tbl_comment", length = 4000)
    private String tblComment;

    @Column(name = "col_comment", length = 4000)
    private String colComment;

    @Column(name = "updt_date")
    private Timestamp updtDate;

    @Column(name = "cd_id")
    private Integer cdId;

    // getter/setter 可根据需要生成

    public static class PK implements Serializable
    {
        private String tid;
        private String colName;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PK pk = (PK) o;
            return java.util.Objects.equals(tid, pk.tid) &&
                    java.util.Objects.equals(colName, pk.colName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tid, colName);
        }
    }
}
