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
 * TB_IMP_CHK_INF 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_CHK_INF")
@Setter
@Getter
@Data
public class ImpChkInf {

    
    // CHK_IDX
    @Id
    @Column(name = "CHK_IDX") 
    private String chkIdx;

    
    // CHK_SENDTYPE

    @Column(name = "CHK_SENDTYPE") 
    private String chkSendtype;

    
    // CHK_MOBILE

    @Column(name = "CHK_MOBILE") 
    private String chkMobile;

    
    // BPNTYPE

    @Column(name = "BPNTYPE") 
    private Long bpntype;

    
    // CHK_KIND

    @Column(name = "CHK_KIND") 
    private String chkKind;

    
    // CHK_SQL

    @Column(name = "CHK_SQL") 
    private String chkSql;

    
    // START_TIME

    @Column(name = "START_TIME") 
    private Date startTime;

    
    // END_TIME

    @Column(name = "END_TIME") 
    private Date endTime;

    
    // ENGINE

    @Column(name = "ENGINE") 
    private String engine;

}