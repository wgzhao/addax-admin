package com.wgzhao.addax.admin.model;

import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "字典编码，外键", example = "1000")
    private Integer dictCode;

    @Id
    @Column(name="item_key", length = 255)
    @Schema(description = "字典项键", example = "SWITCH_TIME")
    private String itemKey;

    @Column(name="item_value", length = 2000)
    @Schema(description = "字典项值", example = "16:30")
    private String itemValue;

    @Column(name="remark", length = 4000)
    @Schema(description = "备注", example = "切日时间")
    private String remark;
}
