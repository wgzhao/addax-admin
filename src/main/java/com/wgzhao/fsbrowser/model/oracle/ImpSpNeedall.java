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
 * TB_IMP_SP_NEEDALL 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_SP_NEEDALL")
@Setter
@Getter
@Data
public class ImpSpNeedall {

    
    // SP_ID
    @Id
    @Column(name = "SP_ID") 
    private String spId;

    
    // 前置数据源

    @Column(name = "NEED_SOU") 
    private String needSou;

    
    // 前置SP

    @Column(name = "NEED_SP") 
    private String needSp;

    
    // 所有前置表

    @Column(name = "SP_ALLTABS") 
    private String spAlltabs;

    
    // SP生成表

    @Column(name = "SP_DEST") 
    private String spDest;

    
    // 穿透后所有前置数据源

    @Column(name = "THROUGH_NEED_SOU") 
    private String throughNeedSou;

    
    // 穿透后所有前置SP

    @Column(name = "THROUGH_NEED_SP") 
    private String throughNeedSp;

}