package com.wgzhao.addax.admin.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "字典编码，主键", example = "1000")
    private Integer code;

    @Column(name = "name")
    @Schema(description = "字典名称", example = "系统参数")
    private String name;

    // 列名在 DDL 中为 "classification "（尾随空格），这里使用转义以精确映射
    @Column(name = "classification")
    @Schema(description = "字典分类", example = "system")
    private String classification;

    @Column(name = "remark")
    @Schema(description = "备注", example = "系统参数相关字典")
    private String remark;
}
