package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.io.Serializable;

@Entity
@Table(name = "tb_imp_etl_soutab")
@IdClass(TbImpEtlSoutab.PK.class)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TbImpEtlSoutab implements Serializable {

    @Column(name = "sou_db_conn", length = 64, nullable = false)
    private String souDbConn;


    @Column(name = "owner", length = 64, nullable = false)
    private String owner;

    @Column(name = "table_name", length = 64, nullable = false)
    private String tableName;

    @Id
    @Column(name = "column_name", length = 64, nullable = false)
    private String columnName;

    @Column(name = "data_type", length = 64)
    private String dataType;

    @Column(name = "data_length")
    private Integer dataLength;

    @Column(name = "data_precision")
    private Integer dataPrecision;

    @Column(name = "data_scale")
    private Integer dataScale;

    @Column(name = "column_id")
    private Integer columnId;

    @Column(name = "table_type", length = 32)
    private String tableType;

    @Column(name = "tab_comment", length = 2000)
    private String tabComment;

    @Column(name = "col_comment", length = 2000)
    private String colComment;

    @Column(name = "dw_clt_date")
    private Timestamp dwCltDate;

    @Id
    @Column(name = "tid", length = 32)
    private String tid;

    // getter/setter 省略，可根据需要生成

    public static class PK implements Serializable {
        private String tid;
        private String columnName;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PK pk = (PK) o;
            return java.util.Objects.equals(tid, pk.tid) &&
                    java.util.Objects.equals(columnName, pk.columnName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tid, columnName);
        }
    }
}
