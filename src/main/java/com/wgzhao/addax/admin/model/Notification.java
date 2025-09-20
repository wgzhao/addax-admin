package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 数据中心消息提醒总表 实体类
 *
 * @author 
 */
@Entity
@Table(name="tb_msg")
@Setter
@Getter
@Data
public class Notification
{

    
    // 自动生成，无需理会
    @Id
    @Column(name = "mid") 
    private String mid;

    
    // 接收人号码或者其他唯一标识，用逗号分隔

    @Column(name = "phone") 
    private String phone;

    
    // 消息内容

    @Column(name = "msg") 
    private String msg;

    
    // 消息生成的时间，自动生成

    @Column(name = "dw_clt_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date dwCltDate;

    
    // 是否发送短信，发送成功后置为y

    @Column(name = "bsms") 
    private String bsms;

    
    // 是否发送KK，发送成功后置为y

    @Column(name = "bkk") 
    private String bkk;

    
    // 是否拨打语音，拨打成功后置为y

    @Column(name = "bcall") 
    private String bcall;

}