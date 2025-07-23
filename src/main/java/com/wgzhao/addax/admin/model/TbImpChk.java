package com.wgzhao.addax.admin.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name="tb_imp_chk")
@Setter
@Getter
@Data
@IdClass(TbImpChkKey.class)
public class TbImpChk {

    // CHK_KIND
    @Column(name = "chk_kind") 
    private String chkKind;

    
    // CHK_NAME
    @Column(name = "chk_name") 
    private String chkName;

    
    // CHK_CONTENT
    @Id
    @Column(name = "chk_content") 
    private String chkContent;

    
    // UPDT_DATE
    @Id
    @Column(name = "updt_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updtDate;

    
    // CHK_MOBILE
    @Column(name = "chk_mobile") 
    private String chkMobile;

    
    // CHK_SENDTYPE
    @Column(name = "chk_sendtype") 
    private String chkSendtype;


}
