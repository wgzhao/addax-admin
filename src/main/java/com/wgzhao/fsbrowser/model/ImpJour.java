package com.wgzhao.fsbrowser.model;

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
 * TB_IMP_JOUR 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_JOUR")
@Setter
@Getter
@Data
public class ImpJour {

    
    // KIND
    @Id
    @Column(name = "KIND") 
    private String kind;

    
    // TRADE_DATE

    @Column(name = "TRADE_DATE") 
    private Long tradeDate;

    
    // STATUS

    @Column(name = "STATUS") 
    private String status;

    
    // KEY_ID

    @Column(name = "KEY_ID") 
    private String keyId;

    
    // REMARK

    @Column(name = "REMARK") 
    private String remark;

    
    // UPDT_DATE

    @Column(name = "UPDT_DATE") 
    private Date updtDate;

}