package com.wgzhao.addax.admin.model;

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

@Entity
@Table(name = "tb_imp_sp_needtab")
@Data
@Setter
@Getter
public class TbImpSpNeedtab {
    @Basic
    @Column(name = "sp_id")
    private String spId;

    @Id
    @Basic
    @Column(name = "table_name")
    private String tableName;
    @Basic
    @Column(name = "updt")
    private Date updt;
    @Basic
    @Column(name = "kind")
    private String kind;

    @Formula("to_char(fn_imp_value('taskname',sp_id))")
    private String used;

    @Formula("decode(kind,'ALL','SP基础表','DS','数据服务源表','NEEDS','SP前置依赖','其他类型')")
    private String kindName;
}
