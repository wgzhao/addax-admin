package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * TMP_IMP_SP_NEEDTAB 实体类
 *
 * @author 
 */
@Entity
@Table(name="TMP_IMP_SP_NEEDTAB")
@Setter
@Getter
@Data
public class TmpImpSpNeedtab {

    
    // SP_ID
    @Id
    @Column(name = "SP_ID") 
    private String spId;

    
    // SP_NAME

    @Column(name = "SP_NAME") 
    private String spName;

    
    // COM_TEXT

    @Column(name = "COM_TEXT") 
    private String comText;

}