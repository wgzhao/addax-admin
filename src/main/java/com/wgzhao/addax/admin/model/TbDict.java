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

@Entity
@Table(name = "tb_dict")
@Setter
@Getter
public class TbDict {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "dict_code")
    private String dictCode;
    @Basic
    @Column(name = "dict_name")
    private String dictName;
    @Basic
    @Column(name = "dict_class")
    private String dictClass;
    @Basic
    @Column(name = "remark")
    private String remark;
}
