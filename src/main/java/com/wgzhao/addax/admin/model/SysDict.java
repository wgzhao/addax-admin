package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sys_dict")
@Setter
@Getter
public class SysDict
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code")
    private Integer code;

    @Column(name = "name")
    private String name;

    // 列名在 DDL 中为 "classification "（尾随空格），这里使用转义以精确映射
    @Column(name = "classification")
    private String classification;

    @Column(name = "remark")
    private String remark;
}
