package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;

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
@Table(name="tb_imp_sp_com")
@Setter
@Getter
@Data
public class ImpSpCom {

    
    // 主表的SP_ID

    @Column(name = "sp_id") 
    private String spId;

    
    // 命令执行顺序

    @Column(name = "com_idx") 
    private Long comIdx;

    
    // 命令类型：hive、presto、spark-sql、shell、spark，参照runcmds.sh的runkind

    @Column(name = "com_kind") 
    private String comKind;

    
    // 命令正文

    @Column(name = "com_text") 
    private String comText;

    
    // 开始时间

    @Column(name = "start_time")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    
    // 结束时间

    @Column(name = "end_time")
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    
    // COM_ID
    @Id
    @Column(name = "com_id") 
    private String comId;

    
    // 状态(包含生成脚本状态、执行情况)

    @Column(name = "flag") 
    private String flag;

}