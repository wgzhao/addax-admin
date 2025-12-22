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
    /**
     * 字典编码，主键
     * 示例: 1000
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "code")
    private Integer code;

    /**
     * 字典名称
     * 示例: 系统参数
     */
    @Column(name = "name")
    private String name;

    // 列名在 DDL 中为 "classification "（尾随空格），这里使用转义以精确映射
    /**
     * 字典分类
     * 示例: system
     */
    @Column(name = "classification")
    private String classification;

    /**
     * 备注
     * 示例: 系统参数相关字典
     */
    @Column(name = "remark")
    private String remark;
}
