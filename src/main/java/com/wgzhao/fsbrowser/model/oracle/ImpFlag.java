package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.*;
import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * TB_IMP_FLAG 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_FLAG")
@Setter
@Getter
@Data
public class ImpFlag {

    
    // TRADEDATE

    @Column(name = "TRADEDATE") 
    private Long tradedate;

    
    // KIND
    @Id
    @Column(name = "KIND") 
    private String kind;

    
    // FID

    @Column(name = "FID") 
    private String fid;

    
    // FVAL

    @Column(name = "FVAL") 
    private String fval;

    
    // DW_CLT_DATE

    @Column(name = "DW_CLT_DATE") 
    private Date dwCltDate;

}