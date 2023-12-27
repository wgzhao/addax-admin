package com.wgzhao.fsbrowser.model.oracle;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "TB_IMP_FLAG")
@Setter
@Getter
public class TbImpFlag {

    @Column(name = "TRADEDATE")
    private Integer tradedate;

    @Column(name = "KIND")
    private String kind;

    @Id
    @Column(name = "FID")
    private String fid;

    @Column(name = "FVAL")
    private String fval;

    @Column(name = "DW_CLT_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date dwCltDate;
}
