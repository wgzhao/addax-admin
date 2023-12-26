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
 * TB_IMP_SP_NEEDTAB 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_SP_NEEDTAB")
@Setter
@Getter
@Data
public class ImpSpNeedtab {

    
    // SP_ID
    @Id
    @Column(name = "SP_ID") 
    private String spId;

    
    // TABLE_NAME

    @Column(name = "TABLE_NAME") 
    private String tableName;

    
    // UPDT

    @Column(name = "UPDT") 
    private Date updt;

    
    // KIND

    @Column(name = "KIND") 
    private String kind;

}