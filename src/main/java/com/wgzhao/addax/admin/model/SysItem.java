package com.wgzhao.addax.admin.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Table(name="sys_item")
@Getter
@Setter
@Data
@Entity
@IdClass(SysItemPK.class)
public class SysItem {
    @Id
    @Column(name="dict_code")
    private short dictCode;

    @Id
    @Column(name="item_key", length = 255)
    private String itemKey;

    @Column(name="item_value", length = 2000)
    private String itemValue;

    @Column(name="remark", length = 4000)
    private String remark;
}
