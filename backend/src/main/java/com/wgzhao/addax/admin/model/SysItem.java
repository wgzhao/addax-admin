package com.wgzhao.addax.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Table(name = "sys_item")
@Getter
@Setter
@Data
@Entity
@IdClass(SysItemPK.class)
public class SysItem
{
    /**
     * 字典编码，外键
     * 示例: 1000
     */
    @Id
    @Column(name = "dict_code")
    private Integer dictCode;

    /**
     * 字典项键
     * 示例: SWITCH_TIME
     */
    @Id
    @Column(name = "item_key", length = 255)
    private String itemKey;

    /**
     * 字典项值
     * 示例: 16:30
     */
    @Column(name = "item_value", length = 2000)
    private String itemValue;

    /**
     * 备注
     * 示例: 切日时间
     */
    @Column(name = "remark", length = 4000)
    private String remark;
}
