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

@Entity
@Table(name = "TB_DICT", schema = "STG01", catalog = "")
@Setter
@Getter
public class TbDict {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "DICT_CODE")
    private String dictCode;
    @Basic
    @Column(name = "DICT_NAME")
    private String dictName;
    @Basic
    @Column(name = "DICT_CLASS")
    private String dictClass;
    @Basic
    @Column(name = "REMARK")
    private String remark;
}
