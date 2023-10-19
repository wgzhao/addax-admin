package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_dict")
@Getter
@Setter
@Data
public class Dict {
    @Id
    @Column(length = 4)
    private String dictCode;
    @Column(length = 255)
    private String dictName;
    @Column(length = 2000)
    private String dictClass;
    @Column(length = 2000)
    private String remark;
}
