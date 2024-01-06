package com.wgzhao.addax.admin.model.oracle;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "TB_IMP_SP_NEEDTAB", schema = "STG01", catalog = "")
@Data
@Setter
@Getter
public class TbImpSpNeedtab {
    @Basic
    @Column(name = "SP_ID")
    private String spId;

    @Id
    @Basic
    @Column(name = "TABLE_NAME")
    private String tableName;
    @Basic
    @Column(name = "UPDT")
    private Date updt;
    @Basic
    @Column(name = "KIND")
    private String kind;

    @Formula("to_char(fn_imp_value('taskname',sp_id))")
    private String used;
}
