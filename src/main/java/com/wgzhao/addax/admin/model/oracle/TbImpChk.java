package com.wgzhao.addax.admin.model.oracle;

import java.util.Date;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * TB_IMP_CHK 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_CHK")
@Setter
@Getter
@Data
public class TbImpChk {

    
    // CHK_KIND
    @Id
    @Column(name = "CHK_KIND") 
    private String chkKind;

    
    // CHK_NAME
    @Column(name = "CHK_NAME") 
    private String chkName;

    
    // CHK_CONTENT
    @Column(name = "CHK_CONTENT") 
    private String chkContent;

    
    // UPDT_DATE
    @Column(name = "UPDT_DATE") 
    private Date updtDate;

    
    // CHK_MOBILE
    @Column(name = "CHK_MOBILE") 
    private String chkMobile;

    
    // CHK_SENDTYPE
    @Column(name = "CHK_SENDTYPE") 
    private String chkSendtype;

}