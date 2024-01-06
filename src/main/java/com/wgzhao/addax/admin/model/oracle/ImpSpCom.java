package com.wgzhao.addax.admin.model.oracle;

import com.fasterxml.jackson.annotation.JsonFormat;
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
 * HADOOP_SP的运行脚本（作为主表的附属表） 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_IMP_SP_COM")
@Setter
@Getter
@Data
public class ImpSpCom {

    
    // 主表的SP_ID

    @Column(name = "SP_ID") 
    private String spId;

    
    // 命令执行顺序

    @Column(name = "COM_IDX") 
    private Long comIdx;

    
    // 命令类型：hive、presto、spark-sql、shell、spark，参照runcmds.sh的runkind

    @Column(name = "COM_KIND") 
    private String comKind;

    
    // 命令正文

    @Column(name = "COM_TEXT") 
    private String comText;

    
    // 开始时间

    @Column(name = "START_TIME")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    
    // 结束时间

    @Column(name = "END_TIME")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    
    // COM_ID
    @Id
    @Column(name = "COM_ID") 
    private String comId;

    
    // 状态(包含生成脚本状态、执行情况)

    @Column(name = "FLAG") 
    private String flag;

}