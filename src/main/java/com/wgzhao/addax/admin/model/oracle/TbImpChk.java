package com.wgzhao.addax.admin.model.oracle;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
@IdClass(TbImpChkKey.class)
public class TbImpChk {

    // CHK_KIND
    @Column(name = "CHK_KIND") 
    private String chkKind;

    
    // CHK_NAME
    @Column(name = "CHK_NAME") 
    private String chkName;

    
    // CHK_CONTENT
    @Id
    @Column(name = "CHK_CONTENT") 
    private String chkContent;

    
    // UPDT_DATE
    @Id
    @Column(name = "UPDT_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtDate;

    
    // CHK_MOBILE
    @Column(name = "CHK_MOBILE") 
    private String chkMobile;

    
    // CHK_SENDTYPE
    @Column(name = "CHK_SENDTYPE") 
    private String chkSendtype;


}
