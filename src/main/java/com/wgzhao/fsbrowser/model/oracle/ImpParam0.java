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
 * 日期参数文件基表 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_PARAM0")
@Setter
@Getter
@Data
public class ImpParam0 {

    
    // PARAM_SOU

    @Column(name = "PARAM_SOU") 
    private String paramSou;

    
    // PARAM_KIND_0
    @Id
    @Column(name = "PARAM_KIND_0") 
    private String paramKind0;

    
    // PARAM_KIND

    @Column(name = "PARAM_KIND") 
    private String paramKind;

    
    // PARAM_NAME

    @Column(name = "PARAM_NAME") 
    private String paramName;

    
    // PARAM_REMARK

    @Column(name = "PARAM_REMARK") 
    private String paramRemark;

    
    // PARAM_VALUE

    @Column(name = "PARAM_VALUE") 
    private String paramValue;

}