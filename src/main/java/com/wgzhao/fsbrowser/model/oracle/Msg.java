package com.wgzhao.fsbrowser.model.oracle;

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
 * 数据中心消息提醒总表 实体类
 *
 * @author 
 */
@Entity
@Table(name="TB_MSG")
@Setter
@Getter
@Data
public class Msg {

    
    // 自动生成，无需理会
    @Id
    @Column(name = "MID") 
    private String mid;

    
    // 接收人号码或者其他唯一标识，用逗号分隔

    @Column(name = "PHONE") 
    private String phone;

    
    // 消息内容

    @Column(name = "MSG") 
    private String msg;

    
    // 消息生成的时间，自动生成

    @Column(name = "DW_CLT_DATE") 
    private Date dwCltDate;

    
    // 是否发送短信，发送成功后置为y

    @Column(name = "BSMS") 
    private String bsms;

    
    // 是否发送KK，发送成功后置为y

    @Column(name = "BKK") 
    private String bkk;

    
    // 是否拨打语音，拨打成功后置为y

    @Column(name = "BCALL") 
    private String bcall;

}